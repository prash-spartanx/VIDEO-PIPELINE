# 🎥 PIB Video Synthesis Service (PVSS)

<div align="center">

![PVSS Banner](https://img.shields.io/badge/PVSS-Automated%20Video%20Generation-blue?style=for-the-badge)

**Transform Government Press Releases into Engaging Multilingual Videos**

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat&logo=openjdk)](https://openjdk.org/)
[![Python](https://img.shields.io/badge/Python-3.10+-blue?style=flat&logo=python)](https://python.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![FastAPI](https://img.shields.io/badge/FastAPI-Latest-teal?style=flat&logo=fastapi)](https://fastapi.tiangolo.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat&logo=docker)](https://docker.com/)
[![License](https://img.shields.io/badge/License-Academic-yellow?style=flat)](LICENSE)

[Features](#-features) • [Quick Start](#-quick-start) • [Architecture](#-architecture) • [Documentation](#-documentation) • [Contributing](#-contributors)

</div>

---

## 📖 About

PVSS is a **production-grade microservices system** that automatically converts Press Information Bureau (PIB) press releases into short, narrated videos (30-60 seconds). Built for the Government of India's digital outreach initiatives, it bridges the gap between textual governance information and multimedia consumption.

### 🎯 Why PVSS?

- **Enhanced Accessibility**: Reach citizens who prefer video content over text
- **Multilingual Support**: Generate content in 14+ Indian languages
- **Automated Pipeline**: From RSS feed to published video in under 2 minutes
- **Government Ready**: Built with security, validation, and approval workflows

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🔄 Automated Content Pipeline
- RSS feed ingestion from PIB
- Intelligent content extraction & validation
- Smart summarization for video scripts

### 🗣️ Advanced Speech Synthesis
- Support for 14 Indian languages
- Adaptive narration styles
- High-fidelity text-to-speech (85%+ accuracy)

</td>
<td width="50%">

### 🎬 Video Generation
- Dynamic video rendering with MoviePy
- Automated editing & transitions
- Template-based visual design

### 🔒 Enterprise Security
- JWT-based authentication
- Role-based access control (RBAC)
- Officer vetting & approval system

</td>
</tr>
</table>

---

## 🏗️ Architecture

```
                    ┌─────────────────────────┐
                    │   PostgreSQL Database   │
                    │   (Metadata Storage)    │
                    └───────────┬─────────────┘
                                │
                         Persists metadata
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  Scraper      │    │  Spring Boot API │    │  Python FastAPI     │
│  (Jsoup)      │───▶│  Gateway (8080)  │───▶│  Engine (8000)      │
│               │RSS │  JWT/RBAC        │REST│  TTS + MoviePy      │
└───────────────┘    └──────────────────┘    └─────────────────────┘
                              │                         │
                              │                         │
                              ▼                         ▼
                    ┌──────────────────────────────────────┐
                    │    Frontend (html,css,javascript)    │
                    │        JWT Authentication            │
                    └──────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend API** | Spring Boot 3.x, Java 21 |
| **Video Engine** | Python 3.10+, FastAPI, MoviePy |
| **Database** | PostgreSQL |
| **Authentication** | JWT, Spring Security |
| **Scraping** | Jsoup, Selenium |
| **TTS** | Improvise API (Multilingual) |
| **Containerization** | Docker, Docker Compose |

---

## 🚀 Quick Start

### Prerequisites

Ensure you have the following installed:

- **Java 21+** ([Download](https://adoptium.net/))
- **Python 3.10+** ([Download](https://python.org/downloads/))
- **Docker Desktop** ([Download](https://docker.com/products/docker-desktop))
- **Git** ([Download](https://git-scm.com/downloads))

### Option 1: Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/yourusername/pvss.git
cd pvss

# Build and start all services
docker compose up -d

# Check service health
docker compose ps
```

**Services will be available at:**
- 🌐 Spring Boot API: http://localhost:8080
- 🐍 Python Synthesis Engine: http://localhost:8000
- 🗄️ PostgreSQL: localhost:5432

```bash
# Stop all services
docker compose down
```

### Option 2: Local Development

#### Backend (Spring Boot)

```bash
cd video-synthesis-service

# Build and run
./mvnw clean install
./mvnw spring-boot:run

# API will start at http://localhost:8080
```

#### Python Synthesis Engine

```bash
cd video-generation-service

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Start FastAPI server
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 📂 Project Structure

```
VIDEO-PIPELINE/
├── video-synthesis-service/      # Spring Boot API Gateway
│   ├── src/
│   │   ├── main/java/
│   │   └── resources/
│   ├── pom.xml
│   └── Dockerfile
│
├── video-generation-service/     # Python Video Engine
│   ├── main.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── pvss_charts/                  # Evaluation & Metrics
│   └── evaluation_toolkit.py
│
├── docker-compose.yml            # Multi-container orchestration
├── README.md
└── LICENSE
```

---

## 🔧 Configuration

### Environment Variables

Create a `.env` file in the root directory:

```env
# Database
POSTGRES_DB=pvss_db
POSTGRES_USER=pvss_user
POSTGRES_PASSWORD=your_secure_password

# JWT Security
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000

# API Keys
TTS_API_KEY=your_improvise_api_key

# Service Ports
SPRING_PORT=8080
FASTAPI_PORT=8000
```

---

## 📊 Performance Metrics

Our system achieves industry-leading performance:

| Metric | Score | Benchmark |
|--------|-------|-----------|
| **Content Extraction Accuracy** | 92% | Industry avg: 85% |
| **Multilingual TTS Fidelity** | >85% | Industry avg: 75% |
| **End-to-End Latency** | <120s/video | Target: <180s |
| **System Uptime** | 99.5% | Target: 99% |

*Metrics measured using evaluation toolkit in `/pvss_charts/`*

---

## 🎓 API Documentation

### Spring Boot Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Authenticate user |
| `GET` | `/api/releases` | Fetch PIB releases |
| `POST` | `/api/video/generate` | Trigger video generation |
| `GET` | `/api/video/{id}` | Retrieve video status |
| `PUT` | `/api/video/{id}/approve` | Approve video (Admin) |

### Python Engine Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/synthesize` | Generate video from text |
| `GET` | `/health` | Service health check |
| `GET` | `/languages` | Supported languages |

📚 **Full API Documentation**: Run the services and visit:
- Swagger UI: http://localhost:8080/swagger-ui.html
- FastAPI Docs: http://localhost:8000/docs

---

## 🐳 Docker Hub Images

Pre-built images are available on Docker Hub:

```bash
# Pull images
docker pull spartanprash/video-synthesis-service:latest
docker pull spartanprash/video-generation-service:latest

# Run containers
docker run -p 8080:8080 spartanprash/video-synthesis-service:latest
docker run -p 8000:8000 spartanprash/video-generation-service:latest
```

---

## 🧪 Testing

### Run Unit Tests

```bash
# Java tests
cd video-synthesis-service
./mvnw test

# Python tests
cd video-generation-service
pytest tests/
```

### Run Integration Tests

```bash
# Start all services
docker compose up -d

# Run test suite
./run_integration_tests.sh
```

---

## 🛠️ Development Workflow

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/AmazingFeature`)
3. **Commit** your changes (`git commit -m 'Add some AmazingFeature'`)
4. **Push** to the branch (`git push origin feature/AmazingFeature`)
5. **Open** a Pull Request

### Code Style

- **Java**: Follow Google Java Style Guide
- **Python**: Follow PEP 8 with Black formatter
- **Git Commits**: Use Conventional Commits

---

## 📈 Roadmap

- [x] Core video generation pipeline
- [x] Multilingual TTS integration
- [x] JWT authentication & RBAC
- [x] Docker containerization
- [ ] Kubernetes deployment manifests
- [ ] Real-time video streaming
- [ ] AI-powered thumbnail generation
- [ ] Analytics dashboard
- [ ] Mobile app integration

---

## 👥 Contributors

<table>
<tr>
<td align="center">
<b>Prashant Naik</b><br>
Backend & Microservices<br>
Docker & Database Integration
</td>
<td align="center">
<b>Bhuvan B. S.</b><br>
Frontend Development<br>
UI/UX Design
</td>
<td align="center">
<b>Pratham S.</b><br>
CI/CD & Testing<br>
Documentation & IEEE Draft
</td>
</tr>
</table>

---

## 🎓 Academic Context

**Institution**: Department of Computer Science and Engineering  
**University**: Presidency University, Bengaluru  
**Batch**: CSE_40  
**Date**: November 2025

This project was developed as part of academic research in automated multimedia content generation and government digital transformation.

---

## 📄 License

This project is developed for **academic research and demonstration purposes**.  
For commercial use or deployment, please contact the contributors.

---

## 🤝 Support

Having trouble? We're here to help!

- 📧 **Email**: naikprashant837@gmail.com
- 💬 **Issues**: [GitHub Issues](https://github.com/prash-spartanx/VIDEO-PIPELINE/issues)
- 📖 **Documentation**: [Wiki](https://github.com//prash-spartanx/VIDEO-PIPELINE/wiki)

---

## 🙏 Acknowledgments

- Press Information Bureau (PIB), Government of India
- Presidency University, Bengaluru
- Open source community for amazing tools and libraries

---

<div align="center">

**Made with ❤️ by the PVSS Team**

[⬆ Back to Top](#-pib-video-synthesis-service-pvss)

<<<<<<< HEAD
</div>
=======
</div>
>>>>>>> 9d793c4f073c8f2eb3d0454d2d8914489eb02962
