from fastapi import FastAPI, HTTPException, Path, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from pymongo import MongoClient
from pymongo.errors import PyMongoError
from bson import ObjectId
import datetime
from typing import List, Optional

# ─── App ──────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="EduNet API",
    version="1.0.0",
    description="""
## EduNet – Smart Attendance Management API

This API powers the **EduNet** Android application, providing:

- 🔐 **Authentication** – Sign up and log in as a teacher or student
- 📚 **Student** – Browse enrolled subjects, join new classes via code
- 🎓 **Teacher** – Create subjects, list enrolled students
- 📋 **Attendance** – Submit and retrieve daily attendance records (with offline-sync support)

### Base URL (local dev)
```
http://<teacher-device-ip>:8000
```
""",
    contact={
        "name": "EduNet Team",
    },
    license_info={
        "name": "MIT",
    },
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── MongoDB ──────────────────────────────────────────────────────────────────
MONGO_URI = (
    "mongodb://root:root123@"
    "ac-ywiif9y-shard-00-00.h1ehyle.mongodb.net:27017,"
    "ac-ywiif9y-shard-00-01.h1ehyle.mongodb.net:27017,"
    "ac-ywiif9y-shard-00-02.h1ehyle.mongodb.net:27017/"
    "?authSource=admin&replicaSet=atlas-uz8cvc-shard-0&tls=true&appName=Techathon"
)

client = MongoClient(MONGO_URI)
db = client["techathon"]
users_col = db["users"]
subjects_col = db["subjects"]
enrollments_col = db["subject_enrollments"]
attendance_col = db["attendance"]


# ─── Request / Response Models ────────────────────────────────────────────────

class LoginRequest(BaseModel):
    email: str = Field(..., description="Registered email address", examples=["john@student.edu"])
    password: str = Field(..., description="Account password", examples=["secret123"])

    model_config = {"json_schema_extra": {"example": {"email": "john@student.edu", "password": "secret123"}}}


class SignUpRequest(BaseModel):
    name: str = Field(..., description="Full name of the user", examples=["John Doe"])
    email: str = Field(..., description="Email address (must be unique)", examples=["john@student.edu"])
    password: str = Field(..., description="Password for the account", examples=["secret123"])
    role: str = Field("student", description="User role: `student` or `teacher`", examples=["student"])

    model_config = {"json_schema_extra": {"example": {"name": "John Doe", "email": "john@student.edu", "password": "secret123", "role": "student"}}}


class AuthResponse(BaseModel):
    success: bool = Field(..., description="Whether the operation succeeded")
    message: str = Field(..., description="Human-readable result message")
    user_id: str = Field("", description="MongoDB ObjectId of the user")
    name: str = Field("", description="Full name of the user")
    email: str = Field("", description="Email address of the user")
    role: str = Field("", description="Role of the user: `student` or `teacher`")


class JoinClassRequest(BaseModel):
    student_id: str = Field("", description="MongoDB ObjectId of the student (preferred)", examples=["64f1a2b3c4d5e6f7a8b9c0d1"])
    student_email: str = Field("", description="Fallback: student's email if `student_id` is not provided", examples=["john@student.edu"])
    subject_code: str = Field(..., description="Subject code shared by the teacher (case-insensitive)", examples=["CS501"])

    model_config = {"json_schema_extra": {"example": {"student_id": "64f1a2b3c4d5e6f7a8b9c0d1", "student_email": "", "subject_code": "CS501"}}}


class SubjectInfo(BaseModel):
    subject_id: str = Field(..., description="MongoDB ObjectId of the subject")
    subject_name: str = Field(..., description="Full name of the subject")
    subject_code: str = Field(..., description="Unique short code for the subject")
    teacher_name: str = Field("", description="Name of the teacher who owns the subject")
    enrolled_at: str = Field("", description="ISO 8601 timestamp of when the student enrolled")


class EnrolledStudentResponse(BaseModel):
    student_id: str = Field(..., description="MongoDB ObjectId of the student")
    student_name: str = Field(..., description="Full name of the student")
    student_email: str = Field(..., description="Email address of the student")


class AttendanceRecordModel(BaseModel):
    student_id: str = Field(..., description="MongoDB ObjectId of the student", examples=["64f1a2b3c4d5e6f7a8b9c0d1"])
    student_name: str = Field(..., description="Full name of the student", examples=["John Doe"])
    status: str = Field(..., description="Attendance status: `present`, `absent`, or `late`", examples=["present"])
    marked_at: str = Field(..., description="ISO 8601 UTC timestamp when the record was created", examples=["2026-02-22T01:30:00Z"])


class SubmitAttendanceRequest(BaseModel):
    subject_code: str = Field(..., description="Subject code (case-insensitive)", examples=["CS501"])
    date: str = Field(..., description="Date of the class in `yyyy-MM-dd` format", examples=["2026-02-22"])
    teacher_id: str = Field(..., description="MongoDB ObjectId of the teacher submitting attendance", examples=["64f1a2b3c4d5e6f7a8b9c0d2"])
    records: List[AttendanceRecordModel] = Field(..., description="List of per-student attendance records")

    model_config = {
        "json_schema_extra": {
            "example": {
                "subject_code": "CS501",
                "date": "2026-02-22",
                "teacher_id": "64f1a2b3c4d5e6f7a8b9c0d2",
                "records": [
                    {"student_id": "64f1a2b3c4d5e6f7a8b9c0d1", "student_name": "John Doe", "status": "present", "marked_at": "2026-02-22T01:30:00Z"},
                    {"student_id": "64f1a2b3c4d5e6f7a8b9c0d3", "student_name": "Jane Smith", "status": "absent", "marked_at": "2026-02-22T01:30:00Z"},
                ]
            }
        }
    }


class CreateSubjectRequest(BaseModel):
    teacher_id: str = Field("", description="MongoDB ObjectId of the teacher (preferred)", examples=["64f1a2b3c4d5e6f7a8b9c0d2"])
    teacher_email: str = Field("", description="Fallback: teacher's email if `teacher_id` is not provided", examples=["prof@uni.edu"])
    subject_name: str = Field(..., description="Full name of the subject", examples=["Data Structures"])
    subject_code: str = Field(..., description="Unique short code for students to join (auto-uppercased)", examples=["CS501"])

    model_config = {"json_schema_extra": {"example": {"teacher_id": "64f1a2b3c4d5e6f7a8b9c0d2", "teacher_email": "", "subject_name": "Data Structures", "subject_code": "CS501"}}}


# ─── Tags metadata ────────────────────────────────────────────────────────────
tags_metadata = [
    {"name": "Health", "description": "Server liveness check"},
    {"name": "Auth", "description": "User registration and login"},
    {"name": "Student", "description": "Student-facing endpoints: enrolled subjects and joining classes"},
    {"name": "Teacher", "description": "Teacher-facing endpoints: subject creation and student roster"},
    {"name": "Attendance", "description": "Submit and retrieve daily attendance records"},
]
app.openapi_tags = tags_metadata


# ─── Health ───────────────────────────────────────────────────────────────────
@app.get(
    "/health",
    tags=["Health"],
    summary="Health check",
    response_description="Server is alive",
)
def health():
    """Returns `{\"status\": \"ok\"}` when the server is running. Use this to verify connectivity before making other requests."""
    return {"status": "ok"}


# ─── Auth ─────────────────────────────────────────────────────────────────────
@app.post(
    "/login",
    tags=["Auth"],
    summary="Log in to an existing account",
    response_model=AuthResponse,
    responses={
        200: {"description": "Login successful – returns user info and role"},
        401: {"description": "Invalid email or password"},
        503: {"description": "Database unreachable"},
    },
)
def login(req: LoginRequest):
    """
    Authenticate a user with **email** and **password**.

    - Returns the user's `user_id`, `name`, `email`, and `role` (`student` | `teacher`).
    - The `user_id` (MongoDB ObjectId as string) is used as the identifier for all subsequent API calls.
    """
    try:
        user = users_col.find_one({"email": req.email, "password": req.password})
        if user is None:
            raise HTTPException(status_code=401, detail="Invalid email or password")
        return AuthResponse(
            success=True,
            message="Login successful",
            user_id=str(user["_id"]),
            name=user.get("name", ""),
            email=user.get("email", req.email),
            role=user.get("role", "student"),
        )
    except HTTPException:
        raise
    except PyMongoError as e:
        raise HTTPException(status_code=503, detail=f"Database error: {str(e)}")


@app.post(
    "/signup",
    tags=["Auth"],
    summary="Create a new account",
    response_model=AuthResponse,
    responses={
        200: {"description": "Account created – returns new user info"},
        409: {"description": "An account with this email already exists"},
        503: {"description": "Database unreachable"},
    },
)
def signup(req: SignUpRequest):
    """
    Register a new user as either a **student** or **teacher**.

    - `role` must be `"student"` or `"teacher"` (defaults to `"student"`).
    - Email must be unique across all users.
    - Returns the new user's `user_id` which must be stored client-side for future calls.
    """
    try:
        if users_col.find_one({"email": req.email}):
            raise HTTPException(status_code=409, detail="An account with this email already exists")
        doc = {
            "name": req.name,
            "email": req.email,
            "password": req.password,
            "role": req.role,
            "created_at": datetime.datetime.utcnow().isoformat() + "Z",
            "is_active": True,
        }
        result = users_col.insert_one(doc)
        return AuthResponse(
            success=True,
            message="Account created successfully",
            user_id=str(result.inserted_id),
            name=req.name,
            email=req.email,
            role=req.role,
        )
    except HTTPException:
        raise
    except PyMongoError as e:
        raise HTTPException(status_code=503, detail=f"Database error: {str(e)}")


# ─── Student ──────────────────────────────────────────────────────────────────
@app.get(
    "/student/subjects/{student_id}",
    tags=["Student"],
    summary="Get enrolled subjects for a student",
    response_description="List of subjects the student is enrolled in",
    responses={
        200: {"description": "Array of SubjectInfo objects (may be empty)"},
        500: {"description": "Unexpected server error"},
    },
)
def get_student_subjects(
    student_id: str = Path(..., description="MongoDB ObjectId of the student", examples=["64f1a2b3c4d5e6f7a8b9c0d1"])
):
    """
    Retrieve all subjects that a student is currently enrolled in.

    - **student_id**: the `user_id` returned from `/login` or `/signup`.
    - Returns an array of subject objects including subject name, code, teacher name, and enrolment timestamp.
    - Returns an empty array `[]` if the student has not joined any classes yet.
    """
    try:
        oid = ObjectId(student_id)
        enrollments = list(enrollments_col.find({"student_id": oid}))
        result = []
        for e in enrollments:
            subj = subjects_col.find_one({"_id": e["subject_id"]})
            if not subj:
                continue
            teacher = users_col.find_one({"_id": subj.get("teacher_id")})
            teacher_name = teacher.get("name", "Unknown") if teacher else "Unknown"
            enrolled_at = e.get("enrolled_at", "")
            if hasattr(enrolled_at, "isoformat"):
                enrolled_at = enrolled_at.isoformat()
            result.append({
                "subject_id": str(subj["_id"]),
                "subject_name": subj.get("subject_name", ""),
                "subject_code": subj.get("subject_code", ""),
                "teacher_name": teacher_name,
                "enrolled_at": str(enrolled_at),
            })
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post(
    "/student/join",
    tags=["Student"],
    summary="Join a class using a subject code",
    responses={
        200: {"description": "Successfully enrolled – returns the joined subject info"},
        400: {"description": "Neither `student_id` nor `student_email` was provided"},
        404: {"description": "Student or subject not found"},
        409: {"description": "Student is already enrolled in this subject"},
        500: {"description": "Unexpected server error"},
    },
)
def join_class(req: JoinClassRequest):
    """
    Enrol a student into a class using the **subject code** shared by their teacher.

    - Provide either `student_id` (ObjectId) or `student_email` as the student identifier.
    - `subject_code` is **case-insensitive** (e.g. `cs501` and `CS501` both work).
    - Returns the subject details on success.

    **Error cases:**
    | Code | Reason |
    |------|--------|
    | 400 | Missing both `student_id` and `student_email` |
    | 404 | No subject found with the given code |
    | 409 | Already enrolled |
    """
    try:
        student_oid = None
        try:
            if req.student_id:
                student_oid = ObjectId(req.student_id)
        except Exception:
            pass

        if student_oid is None:
            if not req.student_email:
                raise HTTPException(status_code=400, detail="student_id or student_email is required")
            student = users_col.find_one({"email": req.student_email})
            if not student:
                raise HTTPException(status_code=404, detail="Student not found")
            student_oid = student["_id"]

        subj = subjects_col.find_one({"subject_code": req.subject_code.upper().strip()})
        if not subj:
            raise HTTPException(status_code=404, detail="Subject not found. Check the class code.")

        existing = enrollments_col.find_one({"student_id": student_oid, "subject_id": subj["_id"]})
        if existing:
            raise HTTPException(status_code=409, detail="You are already enrolled in this subject.")

        enrollments_col.insert_one({
            "student_id": student_oid,
            "subject_id": subj["_id"],
            "enrolled_at": datetime.datetime.utcnow(),
        })
        teacher = users_col.find_one({"_id": subj.get("teacher_id")})
        teacher_name = teacher.get("name", "Unknown") if teacher else "Unknown"
        return {
            "success": True,
            "subject_id": str(subj["_id"]),
            "subject_name": subj.get("subject_name", ""),
            "subject_code": subj.get("subject_code", ""),
            "teacher_name": teacher_name,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── Teacher ──────────────────────────────────────────────────────────────────
@app.get(
    "/teacher/subjects/{teacher_id}",
    tags=["Teacher"],
    summary="Get all subjects created by a teacher",
    response_description="List of subjects with student enrolment counts",
    responses={
        200: {"description": "Array of subject objects (may be empty)"},
        500: {"description": "Unexpected server error"},
    },
)
def get_teacher_subjects(
    teacher_id: str = Path(..., description="MongoDB ObjectId of the teacher", examples=["64f1a2b3c4d5e6f7a8b9c0d2"])
):
    """
    Retrieve all subjects that a teacher has created.

    - **teacher_id**: the `user_id` returned from `/login` or `/signup` for a teacher account.
    - Each subject includes a live `student_count` showing how many students are currently enrolled.
    - Returns an empty array `[]` if the teacher has not created any subjects yet.
    """
    try:
        oid = ObjectId(teacher_id)
        subjects = list(subjects_col.find({"teacher_id": oid}))
        result = []
        for s in subjects:
            count = enrollments_col.count_documents({"subject_id": s["_id"]})
            result.append({
                "subject_id": str(s["_id"]),
                "subject_name": s.get("subject_name", ""),
                "subject_code": s.get("subject_code", ""),
                "student_count": count,
            })
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post(
    "/teacher/subjects",
    tags=["Teacher"],
    summary="Create a new subject",
    responses={
        200: {"description": "Subject created – returns the new subject info including its unique code"},
        400: {"description": "Missing `teacher_id`/`teacher_email`, or blank subject name/code"},
        404: {"description": "Teacher not found (when resolving by email)"},
        409: {"description": "A subject with this code already exists"},
        500: {"description": "Unexpected server error"},
    },
)
def create_subject(req: CreateSubjectRequest):
    """
    Create a new subject that students can join via the returned `subject_code`.

    - Provide either `teacher_id` (ObjectId) or `teacher_email` as the teacher identifier.
    - `subject_code` is **auto-uppercased** and must be unique across all subjects.
    - The returned `subject_code` should be shared with students so they can enrol via `/student/join`.

    **Error cases:**
    | Code | Reason |
    |------|--------|
    | 400 | Missing teacher identifier, blank name, or blank code |
    | 409 | Subject code already taken |
    """
    try:
        teacher_oid = None
        try:
            if req.teacher_id:
                teacher_oid = ObjectId(req.teacher_id)
        except Exception:
            pass

        if teacher_oid is None:
            if not req.teacher_email:
                raise HTTPException(status_code=400, detail="teacher_id or teacher_email required")
            teacher = users_col.find_one({"email": req.teacher_email})
            if not teacher:
                raise HTTPException(status_code=404, detail="Teacher not found")
            teacher_oid = teacher["_id"]

        code = req.subject_code.upper().strip()
        if not code or not req.subject_name.strip():
            raise HTTPException(status_code=400, detail="Subject name and code are required")

        if subjects_col.find_one({"subject_code": code}):
            raise HTTPException(status_code=409, detail=f"Code '{code}' is already taken. Choose another.")

        result = subjects_col.insert_one({
            "subject_name": req.subject_name.strip(),
            "subject_code": code,
            "teacher_id": teacher_oid,
            "is_active": True,
            "created_at": datetime.datetime.utcnow().isoformat() + "Z",
        })
        return {
            "success": True,
            "subject_id": str(result.inserted_id),
            "subject_name": req.subject_name.strip(),
            "subject_code": code,
            "student_count": 0,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get(
    "/teacher/subjects/{subject_code}/students",
    tags=["Teacher"],
    summary="Get all students enrolled in a subject",
    response_description="List of enrolled students with their IDs, names, and emails",
    responses={
        200: {"description": "Array of enrolled student objects"},
        404: {"description": "No subject found with the given code"},
        500: {"description": "Unexpected server error"},
    },
)
def get_subject_students(
    subject_code: str = Path(..., description="Subject code (case-insensitive)", examples=["CS501"])
):
    """
    Retrieve all students currently enrolled in a specific subject.

    - **subject_code**: the unique code of the subject (case-insensitive).
    - Returns each student's `student_id`, `student_name`, and `student_email`.
    - Used by the teacher's attendance screen to build the class register.
    """
    try:
        subj = subjects_col.find_one({"subject_code": subject_code.upper().strip()})
        if not subj:
            raise HTTPException(status_code=404, detail="Subject not found")

        enrollments = list(enrollments_col.find({"subject_id": subj["_id"]}))
        res = []
        for e in enrollments:
            student = users_col.find_one({"_id": e["student_id"]})
            if student:
                res.append({
                    "student_id": str(student["_id"]),
                    "student_name": student.get("name", "Unknown"),
                    "student_email": student.get("email", "")
                })
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─── Attendance ───────────────────────────────────────────────────────────────
@app.post(
    "/attendance/submit",
    tags=["Attendance"],
    summary="Submit (or update) attendance for a session",
    responses={
        200: {"description": "Attendance saved successfully"},
        404: {"description": "Subject not found"},
        500: {"description": "Unexpected server error"},
    },
)
def submit_attendance(req: SubmitAttendanceRequest):
    """
    Save the attendance for an entire class session (one record per student).

    - Uses **upsert** — if a record already exists for the same student + subject + date it is overwritten. Safe to call multiple times.
    - The Android app calls this immediately when online, and queues it for retry when offline.
    - `status` must be one of: `present`, `absent`, `late`.
    - `marked_at` should be an ISO 8601 UTC string, e.g. `"2026-02-22T01:30:00Z"`.

    **Offline-sync note:** the app stores attendance locally first and calls this endpoint when connectivity is restored.
    """
    try:
        teacher_oid = ObjectId(req.teacher_id)
        subj = subjects_col.find_one({"subject_code": req.subject_code.upper().strip()})
        if not subj:
            raise HTTPException(status_code=404, detail="Subject not found")

        for rec in req.records:
            student_oid = ObjectId(rec.student_id)
            query = {
                "subject_code": req.subject_code.upper().strip(),
                "date": req.date,
                "student_id": student_oid
            }
            try:
                dt = datetime.datetime.fromisoformat(rec.marked_at.replace("Z", "+00:00")).astimezone(datetime.timezone.utc).replace(tzinfo=None)
            except Exception:
                dt = datetime.datetime.utcnow()

            update = {
                "$set": {
                    "subject_id": subj["_id"],
                    "student_name": rec.student_name,
                    "status": rec.status,
                    "marked_by": teacher_oid,
                    "marked_at": dt
                }
            }
            attendance_col.update_one(query, update, upsert=True)

        return {"success": True, "message": "Attendance saved successfully"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get(
    "/attendance/{subject_code}/{date}",
    tags=["Attendance"],
    summary="Get attendance records for a subject on a specific date",
    response_description="List of attendance records for all students on the given date",
    responses={
        200: {"description": "Array of attendance record objects (may be empty if no session was held)"},
        500: {"description": "Unexpected server error"},
    },
)
def get_attendance(
    subject_code: str = Path(..., description="Subject code (case-insensitive)", examples=["CS501"]),
    date: str = Path(..., description="Date in `yyyy-MM-dd` format", examples=["2026-02-22"]),
):
    """
    Retrieve all attendance records for a class on a given date.

    - **subject_code**: case-insensitive subject identifier.
    - **date**: the class date in `yyyy-MM-dd` format.
    - Returns an empty array `[]` if no session was held or attendance was not submitted.
    - Used by both the attendance screen (to pre-fill saved data) and the subject history screen.
    """
    try:
        records = list(attendance_col.find({
            "subject_code": subject_code.upper().strip(),
            "date": date
        }))
        res = []
        for r in records:
            dt_str = ""
            if r.get("marked_at"):
                try:
                    dt_str = r["marked_at"].isoformat() + "Z"
                except Exception:
                    pass
            res.append({
                "student_id": str(r["student_id"]),
                "student_name": r.get("student_name", ""),
                "status": r.get("status", ""),
                "marked_at": dt_str
            })
        return res
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
