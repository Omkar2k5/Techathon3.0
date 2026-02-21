export type UserRole = "staff" | "student";

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
}

export interface Subject {
  id: string;
  name: string;
  code: string;
  staffId: string;
  studentCount: number;
}

export interface Assignment {
  id: string;
  subjectId: string;
  name: string;
  sampleFileName: string;
  allowedTypes: string[];
  timeLimitMinutes: number;
  isActive: boolean;
  startedAt: string | null;
  endsAt: string | null;
  submissionCount: number;
  totalStudents: number;
}

export interface Submission {
  id: string;
  assignmentId: string;
  studentId: string;
  studentName: string;
  fileName: string;
  submittedAt: string;
}
