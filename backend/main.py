from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pymongo import MongoClient
from pymongo.errors import PyMongoError
from bson import ObjectId
import datetime

app = FastAPI(title="EduNet API", version="1.0.0")

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



# ─── Models ───────────────────────────────────────────────────────────────────
class LoginRequest(BaseModel):
    email: str
    password: str

class SignUpRequest(BaseModel):
    name: str
    email: str
    password: str
    role: str = "student"

class AuthResponse(BaseModel):
    success: bool
    message: str
    user_id: str = ""
    name: str = ""
    email: str = ""
    role: str = ""

class JoinClassRequest(BaseModel):
    student_id: str = ""
    student_email: str = ""
    subject_code: str

class SubjectInfo(BaseModel):
    subject_id: str
    subject_name: str
    subject_code: str
    teacher_name: str = ""
    enrolled_at: str = ""

class AttendanceRecordModel(BaseModel):
    student_id: str
    student_name: str
    status: str # "present", "absent", "late"
    marked_at: str

class SubmitAttendanceRequest(BaseModel):
    subject_code: str
    date: str # yyyy-MM-dd
    teacher_id: str
    records: list[AttendanceRecordModel]


# ─── Auth ─────────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/login", response_model=AuthResponse)
def login(req: LoginRequest):
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

@app.post("/signup", response_model=AuthResponse)
def signup(req: SignUpRequest):
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


# ─── Student: Get Enrolled Subjects ───────────────────────────────────────────
@app.get("/student/subjects/{student_id}")
def get_student_subjects(student_id: str):
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


# ─── Student: Join Class via Subject Code ─────────────────────────────────────
@app.post("/student/join")
def join_class(req: JoinClassRequest):
    try:
        # Resolve student: try by id first, fall back to email
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

        # Look up subject by code (case-insensitive)
        subj = subjects_col.find_one({"subject_code": req.subject_code.upper().strip()})
        if not subj:
            raise HTTPException(status_code=404, detail="Subject not found. Check the class code.")

        # Already enrolled?
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


# ─── Teacher: Get My Subjects ─────────────────────────────────────────────────
@app.get("/teacher/subjects/{teacher_id}")
def get_teacher_subjects(teacher_id: str):
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


# ─── Teacher: Create Subject ──────────────────────────────────────────────────
class CreateSubjectRequest(BaseModel):
    teacher_id: str = ""
    teacher_email: str = ""
    subject_name: str
    subject_code: str

@app.post("/teacher/subjects")
def create_subject(req: CreateSubjectRequest):
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


# ─── Teacher & Attendance ─────────────────────────────────────────────────────
@app.get("/teacher/subjects/{subject_code}/students")
def get_subject_students(subject_code: str):
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

@app.post("/attendance/submit")
def submit_attendance(req: SubmitAttendanceRequest):
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
            
            # parse iso marked_at
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

@app.get("/attendance/{subject_code}/{date}")
def get_attendance(subject_code: str, date: str):
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
