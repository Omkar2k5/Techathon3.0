# EduNet Lab Portal
LAN-Based Practical Lab Management System

EduNet Lab Portal is a college lab management system that enables staff to conduct practical sessions and students to enroll in subjects and submit lab work. The system uses a Python/FastAPI backend connected to a MongoDB Atlas cloud database, and a React/TypeScript frontend.

---

## Problem Statement

Most existing college lab management systems depend on internet and centralized servers, making them unreliable in low-resource or poor-network environments. EduNet Lab Portal provides a structured, cloud-backed solution for managing practical sessions with role-based access for teachers and students.

---

## Objectives

- Enable staff (teachers) to create and manage subjects and lab sessions efficiently
- Allow students to discover and join subjects using a class code
- Enforce role-based access control for teachers and students
- Provide a clean, modern web interface for both roles
- Sync data reliably via MongoDB Atlas

---

## Key Features

- Role-based authentication (teacher / student) via email & password
- Student self-registration (signup) and login
- Subject creation by teachers with unique subject codes
- Students can join any subject using a class code
- Enrolled subject listing per student
- Teacher dashboard showing subject-wise student enrollment counts
- RESTful API with FastAPI and automatic OpenAPI docs

---

## High-Level Architecture

```
React Frontend (Vite + TypeScript)
        |
        | HTTP/REST API calls
        v
Python FastAPI Backend (port 8000)
        |
        | PyMongo driver (SRV URI)
        v
MongoDB Atlas (techathon database)
   ├── users
   ├── subjects
   └── subject_enrollments
```

---

## Technology Stack

### Backend
- **Language**: Python 3.11+
- **Framework**: FastAPI 0.111
- **Server**: Uvicorn (ASGI)
- **Database Driver**: PyMongo 4.7 (with SRV support)
- **Validation**: Pydantic v2

### Frontend
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite
- **Styling**: Tailwind CSS + shadcn/ui (Radix UI)
- **HTTP Client**: Native `fetch` API
- **State Management**: TanStack React Query

### Database
- **MongoDB Atlas** (cloud-hosted)
- **Database Name**: `techathon`
- **Connection**: SRV URI (see setup below)

---

## Database Collections

| Collection            | Purpose                                          |
|-----------------------|--------------------------------------------------|
| `users`               | Stores all teacher and student accounts          |
| `subjects`            | Stores subjects created by teachers              |
| `subject_enrollments` | Tracks which students are enrolled in which subjects |

### `users` document structure
```json
{
  "_id": ObjectId,
  "name": "string",
  "email": "string",
  "password": "string (plain text)",
  "role": "teacher | student",
  "roll_no": "string | null",
  "is_active": true,
  "created_at": "ISO datetime string"
}
```

### `subjects` document structure
```json
{
  "_id": ObjectId,
  "subject_name": "string",
  "subject_code": "string (unique, uppercase)",
  "teacher_id": ObjectId,
  "is_active": true,
  "created_at": "ISO datetime string"
}
```

### `subject_enrollments` document structure
```json
{
  "_id": ObjectId,
  "student_id": ObjectId,
  "subject_id": ObjectId,
  "enrolled_at": "datetime"
}
```

---

## API Endpoints

| Method | Endpoint                          | Description                              |
|--------|-----------------------------------|------------------------------------------|
| GET    | `/health`                         | Health check                             |
| POST   | `/login`                          | Login with email & password              |
| POST   | `/signup`                         | Create a new student or teacher account  |
| GET    | `/student/subjects/{student_id}`  | Get all subjects a student is enrolled in|
| POST   | `/student/join`                   | Enroll a student in a subject by code    |
| GET    | `/teacher/subjects/{teacher_id}`  | Get all subjects created by a teacher    |
| POST   | `/teacher/subjects`               | Create a new subject                     |

> **API Docs**: FastAPI auto-generates interactive API docs at `http://localhost:8000/docs` when running locally.

---

## Teacher Workflow

1. Login with teacher email and password (e.g., `ahmed@edunet.in` / `teacher123`)
2. View all subjects you have created on the dashboard
3. Create a new subject by providing a **subject name** and a **unique subject code**
4. Share the subject code with students so they can join
5. View enrollment counts per subject

---

## Student Workflow

1. Sign up with name, email, password and role `student` (or login if already registered)
2. View your enrolled subjects on the dashboard
3. Use the **"Join Class"** button and enter the subject code provided by your teacher to enroll
4. Enrolled subjects appear on your dashboard with the teacher name

---

## Setup Instructions

### Prerequisites
- Python 3.11 or higher
- Node.js 18 or higher
- A MongoDB Atlas account with the `techathon` database and the three collections above

---

### 1. Backend Setup

```bash
# Navigate to the backend folder
cd backend

# Create and activate a virtual environment (recommended)
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

# Install dependencies
pip install -r requirements.txt

# Start the backend server
uvicorn main:app --reload --port 8000
```

The backend will be available at: `http://localhost:8000`
Interactive API docs: `http://localhost:8000/docs`

> **MongoDB URI**: The connection string in `backend/main.py` is already configured to connect to the MongoDB Atlas `techathon` database. If credentials change, update the `MONGO_URI` variable in `main.py`.

---

### 2. Frontend Setup

> **Note**: The frontend lives in the `webpage` folder inside the `Technathon 3.0 Wasim` directory. Run it from there.

```bash
# Navigate to the frontend folder
cd "../Technathon 3.0 Wasim/webpage"

# Install dependencies (first time only)
npm install

# Start the development server
npm run dev
```

The frontend will be available at: `http://localhost:5173`

> The Vite dev server is configured to proxy all `/api` requests to the Rust backend on port 8080. For the Python backend, make sure the frontend API calls point to `http://localhost:8000`.

---

## Network Ports

| Service            | Port   |
|--------------------|--------|
| Python FastAPI     | 8000   |
| Vite Dev Server    | 5173   |
| Rust Backend (EduNet / Wasim) | 8080 |
| MongoDB Atlas      | Cloud (SRV) |

---

## Sample Test Credentials

### Teachers
| Name               | Email              | Password     |
|--------------------|-------------------|--------------|
| Prof. Ahmed Khan   | ahmed@edunet.in   | teacher123   |
| Dr. Priya Sharma   | priya@edunet.in   | teacher123   |

### Students
| Name         | Roll No    | Email                              | Password    |
|--------------|------------|----------------------------------  |-------------|
| Aarav Mehta  | 21AIML101  | 21aiml101@student.edunet.in        | student123  |
| Sneha Patil  | 21AIML102  | abc@gmail.com                      | student123  |
| Rohan Desai  | 21AIML103  | 21aiml103@student.edunet.in        | student123  |

---

## Testing Checklist

- [ ] Teacher can log in and view their subjects
- [ ] Teacher can create a new subject with a unique code
- [ ] New student can sign up and log in
- [ ] Student can join a subject using the class code
- [ ] Student cannot join the same subject twice
- [ ] Student dashboard shows all enrolled subjects with teacher names
- [ ] Invalid subject codes are rejected with the correct error message

---

## Security Notes

- Passwords are currently stored in **plain text** — this should be hashed (e.g., bcrypt) before production deployment
- CORS is currently open to all origins (`*`) — restrict to your frontend origin in production
- MongoDB credentials are hardcoded — move to environment variables (`.env`) before deploying

---

## Future Enhancements

- Password hashing and secure session management (JWT)
- Assignment creation and submission workflow
- Attendance tracking
- AI-assisted feedback via NeuroMesh integration
- Detailed analytics dashboard for teachers
- Plagiarism detection for code submissions

---

## Contributors

Aman Shaikh
Wasim Pathan
Omkar Gondkar
Atharva Mashale
