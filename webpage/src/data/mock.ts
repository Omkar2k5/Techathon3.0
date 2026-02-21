import { Subject, Assignment, Submission } from "@/types";

export const staffSubjects: Subject[] = [
  { id: "s1", name: "Data Structures", code: "CS201", staffId: "staff1", studentCount: 45 },
  { id: "s2", name: "Operating Systems", code: "CS301", staffId: "staff1", studentCount: 38 },
  { id: "s3", name: "Python Programming", code: "CS101", staffId: "staff1", studentCount: 60 },
  { id: "s4", name: "Computer Networks", code: "CS401", staffId: "staff1", studentCount: 42 },
];

export const studentSubjects: Subject[] = [
  { id: "s1", name: "Data Structures", code: "CS201", staffId: "staff1", studentCount: 45 },
  { id: "s3", name: "Python Programming", code: "CS101", staffId: "staff1", studentCount: 60 },
];

export const assignments: Assignment[] = [
  {
    id: "a1", subjectId: "s1", name: "Linked List Implementation",
    sampleFileName: "linked_list_template.cpp", allowedTypes: [".cpp"],
    timeLimitMinutes: 120, isActive: true, startedAt: "2026-02-21T10:00:00",
    endsAt: "2026-02-21T12:00:00", submissionCount: 32, totalStudents: 45,
  },
  {
    id: "a2", subjectId: "s1", name: "Binary Tree Traversal",
    sampleFileName: "binary_tree_template.cpp", allowedTypes: [".cpp"],
    timeLimitMinutes: 120, isActive: false, startedAt: null,
    endsAt: null, submissionCount: 0, totalStudents: 45,
  },
];

export const submissions: Submission[] = [
  { id: "sub1", assignmentId: "a1", studentId: "stu1", studentName: "Alice Johnson", fileName: "linked_list.cpp", submittedAt: "2026-02-21T11:30:00" },
  { id: "sub2", assignmentId: "a1", studentId: "stu2", studentName: "Bob Smith", fileName: "linked_list.cpp", submittedAt: "2026-02-21T11:45:00" },
  { id: "sub3", assignmentId: "a1", studentId: "stu3", studentName: "Charlie Davis", fileName: "linked_list.cpp", submittedAt: "2026-02-21T11:50:00" },
];
