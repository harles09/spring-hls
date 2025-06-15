# Spring Boot Video Streaming Starter

This is a Spring Boot starter project for video upload, conversion to HLS using FFmpeg, storage in MinIO, and metadata persistence in MongoDB.

---

## 🛠️ Stack

* **Spring Boot** – Java backend framework
* **MinIO** – S3-compatible object storage for storing HLS files
* **MongoDB** – NoSQL database for metadata and logs
* **FFmpeg** – Video processing tool to convert files to HLS format (.m3u8 and .ts)
* **HLS (HTTP Live Streaming)** – Used for adaptive bitrate streaming

---

## 📦 Features

* Upload video file (MP4, MKV, etc.)
* Convert video to HLS format using FFmpeg
* Store generated HLS files in MinIO
* Save metadata (filename, duration, format, etc.) in MongoDB
* Stream video via HLS `.m3u8` playlist

---

## 🚀 Getting Started

### 1. Clone the Project

```bash
git clone https://github.com/your-username/springboot-video-hls.git
cd springboot-video-hls
```

### 2. Prerequisites

* Java 21+
* Docker & Docker Compose
* FFmpeg (installed on host machine or containerized)
* Maven

### 3. Run Services with Docker Compose

```bash
docker-compose up -d
```

This spins up:

* **MongoDB** (port `27017`)
* **MinIO** (console at `http://localhost:9001`, S3 at `http://localhost:9000`)

### 4. Configure `.env` (Optional)

```env
MINIO_ENDPOINT=http://localhost:9000
MINIO_ROOT_PASSWORD: 12345678

MONGO_URI=mongodb://mongoadmin:mongopassword@localhost:27017/admin?authSource=admin

FFMPEG_PATH=/usr/bin/ffmpeg  # Adjust if FFmpeg is installed elsewhere
```

### 5. Build & Run Spring Boot App

```bash
./mvnw spring-boot:run
```

---

## 🧪 API Endpoints

| Method | Endpoint                 | Description              |
| ------ |--------------------------|--------------------------|
| POST   | `/file/upload`           | Upload and convert video |
| GET    | `/videos/{videoId}/{fileName:.+}`     | Get video metadata       |
| GET    | `/keys/{videoId}` | Get Video Keys           |

---

## 🧰 Folder Structure

```
rc/main/java/com/example/
├── config → Configuration for MinIO, MongoDB, and FFmpeg
├── controller → REST controllers for handling HTTP requests
├── dto → Data Transfer Objects used in API requests/responses
├── repository → MongoDB repositories (Spring Data)
├── service → Business logic for video handling and processing
├── util → Utility classes (e.g., FFmpeg command builder, file helpers)
└── model → MongoDB document models and entities
```

---

## 📺 HLS Output Example

```
videoId/
├— playlist.m3u8
├— segment0.ts
├— segment1.ts
└— ...
```

---

## 📌 Notes

* Make sure FFmpeg is installed and accessible.
* MinIO bucket must exist or be created programmatically.
* Adjust max upload size in `application.yml` as needed.

---

## 🚜 Clean Up

```bash
docker-compose down -v
```

---

## 📄 License

This project is open-source and free to use under the [MIT License](LICENSE).
