// Central API service — wraps all backend calls

const BASE = "";  // Vite proxies /api → backend at :8080 in dev

// ── Auth ────────────────────────────────────────────────
export interface LoginResponse {
    id: string;
    name: string;
    role: string;
    roll_no?: string;
    email?: string;
}

export async function apiLogin(
    identifier: string,
    password: string,
    expected_role: "teacher" | "student"
): Promise<LoginResponse> {
    const res = await fetch(`${BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identifier, password, expected_role }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Login failed");
    return data;
}

// ── Subjects ─────────────────────────────────────────────
export interface Subject {
    id: string;
    subject_name: string;
    subject_code: string;
}

export async function getTeacherSubjects(teacherId: string): Promise<Subject[]> {
    const res = await fetch(`${BASE}/api/subjects/teacher/${teacherId}`);
    return res.ok ? res.json() : [];
}

export async function getStudentSubjects(studentId: string): Promise<Subject[]> {
    const res = await fetch(`${BASE}/api/subjects/student/${studentId}`);
    return res.ok ? res.json() : [];
}

export async function createSubject(
    subject_name: string,
    subject_code: string,
    teacher_id: string
): Promise<{ id: string }> {
    const res = await fetch(`${BASE}/api/subjects/create`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ subject_name, subject_code, teacher_id }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Failed to create subject");
    return data;
}

// ── Assignments ───────────────────────────────────────────
export interface Assignment {
    id: string;
    assignment_name: string;
    allowed_file_types: string[];
    time_limit_minutes: number;
    deadline: string;
    is_active: boolean;
    created_at?: string;
}

export interface ActiveAssignment extends Assignment {
    subject_id: string;
    subject_name: string;
    subject_code: string;
    has_sample: boolean;
}

export async function getSubjectAssignments(subjectId: string): Promise<Assignment[]> {
    const res = await fetch(`${BASE}/api/assignments/subject/${subjectId}`);
    return res.ok ? res.json() : [];
}

export async function getActiveAssignments(studentId: string): Promise<ActiveAssignment[]> {
    const res = await fetch(`${BASE}/api/assignments/active?student_id=${studentId}`);
    return res.ok ? res.json() : [];
}

export async function createAssignment(formData: FormData): Promise<{ id: string }> {
    const res = await fetch(`${BASE}/api/assignments/create`, { method: "POST", body: formData });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Failed to create assignment");
    return data;
}

export async function startAssignment(id: string): Promise<void> {
    await fetch(`${BASE}/api/assignments/start/${id}`, { method: "POST" });
}

export async function closeAssignment(id: string): Promise<void> {
    await fetch(`${BASE}/api/assignments/close/${id}`, { method: "POST" });
}

// ── Submissions ───────────────────────────────────────────
export interface Submission {
    id: string;
    roll_no: string;
    file_path: string;
    submitted_at: string;
    status: string;
}

export async function getSubmissions(assignmentId: string): Promise<Submission[]> {
    const res = await fetch(`${BASE}/api/assignments/${assignmentId}/submissions`);
    return res.ok ? res.json() : [];
}

export async function uploadFile(formData: FormData): Promise<{ status: string; file: string }> {
    const res = await fetch(`${BASE}/api/upload`, { method: "POST", body: formData });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Upload failed");
    return data;
}

export function exportCsvUrl(assignmentId: string): string {
    return `${BASE}/api/assignments/${assignmentId}/export`;
}

export function sampleFileUrl(assignmentId: string): string {
    return `${BASE}/api/assignments/${assignmentId}/sample`;
}

export async function getSubmissionStatus(assignmentId: string, studentId: string) {
    const res = await fetch(`${BASE}/api/submissions/status?assignment_id=${assignmentId}&student_id=${studentId}`);
    return res.ok ? res.json() : null;
}

// ── Chat ──────────────────────────────────────────────────
export interface ChatMessage {
    role: "user" | "assistant";
    content: string;
}

export interface ChatPayload {
    message: string;
    user_id: string;
    user_role: string;
    assignment_id?: string;
}

export interface ChatReply {
    reply: string;
    mode: string;
    subject_name: string;
}

export async function sendChatMessage(payload: ChatPayload): Promise<ChatReply> {
    const res = await fetch(`${BASE}/api/lab/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error("Chat request failed");
    return res.json();
}

