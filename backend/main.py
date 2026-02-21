from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pymongo import MongoClient
from pymongo.errors import PyMongoError
import datetime

app = FastAPI(title="EduNet API", version="1.0.0")

# Allow all origins (for local dev / Android device on same network)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ─── MongoDB Connection ───────────────────────────────────────────────────────
MONGO_URI = (
    "mongodb://root:root123@"
    "ac-ywiif9y-shard-00-00.h1ehyle.mongodb.net:27017,"
    "ac-ywiif9y-shard-00-01.h1ehyle.mongodb.net:27017,"
    "ac-ywiif9y-shard-00-02.h1ehyle.mongodb.net:27017/"
    "?authSource=admin&replicaSet=atlas-uz8cvc-shard-0&tls=true&appName=Techathon"
)

client = MongoClient(MONGO_URI)
db = client["techathon"]        # ← correct database
users = db["users"]             # ← correct collection



# ─── Request / Response Models ────────────────────────────────────────────────
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
    name: str = ""
    email: str = ""
    role: str = ""


# ─── Routes ──────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/login", response_model=AuthResponse)
def login(req: LoginRequest):
    try:
        user = users.find_one({"email": req.email, "password": req.password})
        if user is None:
            raise HTTPException(status_code=401, detail="Invalid email or password")
        return AuthResponse(
            success=True,
            message="Login successful",
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
        if users.find_one({"email": req.email}):
            raise HTTPException(status_code=409, detail="An account with this email already exists")

        doc = {
            "name": req.name,
            "email": req.email,
            "password": req.password,  # plain text, matching existing DB format
            "role": req.role,
            "created_at": datetime.datetime.utcnow().isoformat() + "Z",
            "is_active": True,
        }
        users.insert_one(doc)
        return AuthResponse(
            success=True,
            message="Account created successfully",
            name=req.name,
            email=req.email,
            role=req.role,
        )
    except HTTPException:
        raise
    except PyMongoError as e:
        raise HTTPException(status_code=503, detail=f"Database error: {str(e)}")
