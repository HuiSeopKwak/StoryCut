import cv2
import numpy as np
import torch
import subprocess
import insightface
from deep_sort_realtime.deepsort_tracker import DeepSort
from multiprocessing import Process
import os
import time
from uuid import uuid4

UPLOAD_DIR = "app/videos"
os.makedirs(UPLOAD_DIR, exist_ok=True)

_face_model = None

def get_face_model():
    global _face_model
    if _face_model is None:
        print("🔄 InsightFace 모델 초기화 중...")
        _face_model = insightface.app.FaceAnalysis()
        _face_model.prepare(ctx_id=0)
    return _face_model

def detect_faces(frame):
    face_model = get_face_model()
    faces = face_model.get(frame)
    if not faces:
        return np.array([]), np.array([])
    boxes = np.array([face.bbox for face in faces])
    embeddings = np.array([face.embedding for face in faces])
    return boxes, embeddings

def cosine_similarity_batch(vecs, target_vec):
    norm_vecs = np.linalg.norm(vecs, axis=1)
    norm_target = np.linalg.norm(target_vec)
    return vecs @ target_vec / (norm_vecs * norm_target)

def get_detections(frame, target_embeddings):
    boxes, embeddings = detect_faces(frame)
    detections = []
    if len(embeddings) > 0:
        for i, emb in enumerate(embeddings):
            similarities = [cosine_similarity_batch(np.array([emb]), t_emb)[0] for t_emb in target_embeddings]
            max_sim = max(similarities)
            x1, y1, x2, y2 = map(int, boxes[i])
            w, h = x2 - x1, y2 - y1
            label = 'target' if max_sim > 0.3 else 'others'
            detections.append(([x1, y1, w, h], 0.99, label))
    return detections

def iou(box1, box2):
    x1, y1, x2, y2 = box1
    x1g, y1g, x2g, y2g = box2
    xi1, yi1 = max(x1, x1g), max(y1, y1g)
    xi2, yi2 = min(x2, x2g), min(y2, y2g)
    inter_area = max(0, xi2 - xi1) * max(0, yi2 - yi1)
    union_area = (x2 - x1) * (y2 - y1) + (x2g - x1g) * (y2g - y1g) - inter_area
    return inter_area / union_area if union_area else 0

def match_detections_to_tracks(tracks, detections):
    track_id_to_class = {}
    for track in tracks:
        if not track.is_confirmed() or track.time_since_update > 0:
            continue
        track_bbox = track.to_ltrb()
        best_iou, best_class = 0, 'others'
        for (x, y, w, h), _, label in detections:
            det_box = [x, y, x + w, y + h]
            iou_score = iou(track_bbox, det_box)
            if iou_score > best_iou:
                best_iou, best_class = iou_score, label
        track_id_to_class[track.track_id] = best_class
    return track_id_to_class

def mosaic_face(frame, x1, y1, x2, y2, scale=0.07):
    height, width = frame.shape[:2]
    x1, x2 = max(0, x1), min(width - 1, x2)
    y1, y2 = max(0, y1), min(height - 1, y2)
    if x2 <= x1 or y2 <= y1:
        return frame
    face = frame[y1:y2, x1:x2]
    small = cv2.resize(face, (max(1, int((x2 - x1) * scale)), max(1, int((y2 - y1) * scale))))
    mosaic = cv2.resize(small, (x2 - x1, y2 - y1), interpolation=cv2.INTER_NEAREST)
    frame[y1:y2, x1:x2] = mosaic
    return frame

def process_tracks(frame, tracks, track_id_to_class):
    for track in tracks:
        if not track.is_confirmed() or track.time_since_update > 0:
            continue
        l, t, r, b = map(int, track.to_ltrb())
        if track_id_to_class.get(track.track_id, 'others') == 'others':
            frame = mosaic_face(frame, l, t, r, b)

def process_video_segment(input_path, target_embeddings, output_path, start_frame, end_frame, detect_interval):
    try:
        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            raise RuntimeError(f"비디오 열기 실패: {input_path}")
        cap.set(cv2.CAP_PROP_POS_FRAMES, start_frame)
        fps = cap.get(cv2.CAP_PROP_FPS)
        width, height = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)), int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        out = cv2.VideoWriter(output_path, cv2.VideoWriter_fourcc(*'mp4v'), fps, (width, height))

        tracker = DeepSort(max_age=50, n_init=3)

        frame_idx = start_frame
        detections = []
        track_id_to_class = {}
        while cap.isOpened() and frame_idx < end_frame:
            ret, frame = cap.read()
            if not ret:
                break
            if frame_idx % detect_interval == 0:
                detections = get_detections(frame, target_embeddings)
            tracks = tracker.update_tracks(detections, frame=frame)
            if frame_idx % detect_interval == 0:
                track_id_to_class = match_detections_to_tracks(tracks, detections)
            process_tracks(frame, tracks, track_id_to_class)
            out.write(frame)
            frame_idx += 1

        cap.release()
        out.release()
    except Exception as e:
        print(f"세그먼트 처리 오류: {e}")

def merge_video_segments(output_path, segment_paths):
    try:
        with open("segments.txt", "w") as f:
            for path in segment_paths:
                f.write(f"file '{path}'\n")
        subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", "segments.txt", "-c", "copy", output_path], check=True)
        os.remove("segments.txt")
    except Exception as e:
        print(f" 병합 오류: {e}")

def add_audio_to_video(video_no_audio_path, original_video_path, output_path):
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries",
             "stream=index", "-of", "csv=p=0", original_video_path],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        has_audio = result.stdout.strip() != ""

        if has_audio:
            subprocess.run([
                "ffmpeg", "-y",
                "-i", video_no_audio_path,
                "-i", original_video_path,
                "-c", "copy",
                "-map", "0:v:0", "-map", "1:a:0",
                "-shortest", output_path
            ], check=True)
        else:
            print("⚠️ 원본 영상에 오디오 없음 — 비디오만 복사합니다.")
            subprocess.run([
                "ffmpeg", "-y",
                "-i", video_no_audio_path,
                "-c", "copy",
                output_path
            ], check=True)

    except subprocess.CalledProcessError as e:
        print(f"❌ 오디오 추가 실패: {e}")
        raise

def gpu_encode_video(input_path: str, output_path: str, bitrate="10M", preset="fast"):
    if not os.path.exists(input_path):
        raise FileNotFoundError(f"입력 파일이 존재하지 않습니다: {input_path}")
    cmd = [
        "ffmpeg", "-y",
        "-i", input_path,
        "-c:v", "h264_nvenc",
        "-preset", preset,
        "-b:v", bitrate,
        output_path
    ]
    print(" GPU 인코딩 시작...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        error_msg = f" 인코딩 실패:\n{result.stderr}"
        print(error_msg)
        raise RuntimeError(error_msg)
    else:
        print(f"✅ 인코딩 완료: {output_path}")

def split_frames(total_frames, num_segments):
    ranges = []
    step = total_frames // num_segments
    for i in range(num_segments):
        start = i * step
        end = total_frames if i == num_segments - 1 else (i + 1) * step
        ranges.append((start, end))
    return ranges

def run_mosaic_pipeline(input_path: str, target_paths: list[str], detect_interval: int = 5, num_segments: int = 3) -> str:
    segment_paths = []
    merged_output = ""
    final_output = ""
    encoded_output = ""

    try:
        target_embeddings = []
        for path in target_paths[:2]:
            img = cv2.imread(path)
            if img is None:
                raise FileNotFoundError(f"이미지를 불러올 수 없습니다: {path}")
            _, embeddings = detect_faces(img)
            if not embeddings.any():
                raise ValueError(f"타깃 얼굴을 찾을 수 없습니다: {path}")
            target_embeddings.append(embeddings[0])

        cap = cv2.VideoCapture(input_path)
        if not cap.isOpened():
            raise RuntimeError(f"비디오 파일을 열 수 없습니다: {input_path}")
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        cap.release()

        segment_ranges = split_frames(total_frames, num_segments)
        segment_paths = [f"{UPLOAD_DIR}/segment_{i}_{uuid4().hex}.mp4" for i in range(num_segments)]
        merged_output = f"{UPLOAD_DIR}/merged_{uuid4().hex}.mp4"
        final_output = f"{UPLOAD_DIR}/final_{uuid4().hex}.mp4"
        encoded_output = f"{UPLOAD_DIR}/encoded_{uuid4().hex}.mp4"

        processes = []
        for i, (start, end) in enumerate(segment_ranges):
            p = Process(target=process_video_segment,
                        args=(input_path, target_embeddings, segment_paths[i], start, end, detect_interval))
            p.start()
            processes.append(p)
        for p in processes:
            p.join()

        merge_video_segments(merged_output, segment_paths)
        add_audio_to_video(merged_output, input_path, final_output)
        gpu_encode_video(final_output, encoded_output)

        return encoded_output

    except Exception as e:
        print(f" 전체 파이프라인 실패: {e}")
        raise

    finally:
        temp_files = segment_paths + [merged_output, final_output] + target_paths
        for path in temp_files:
            try:
                if os.path.exists(path):
                    os.remove(path)
            except Exception as e:
                print(f"임시파일 삭제 오류: {e}")
