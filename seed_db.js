/**
 * EduNet Lab Portal — MongoDB Seed Script
 * Run with: mongosh mongodb://localhost:27017/neuromesh_lab seed_db.js
 *
 * Creates:
 *   - 2 teachers
 *   - 6 students
 *   - 3 subjects
 *   - Enrollments linking students to subjects
 */

// ── Passwords stored as plain text (backend checks plain text in role_auth.rs) ──
// Update the backend to use bcrypt later for production.

// ── Clear existing data ──
db.users.drop();
db.subjects.drop();
db.subject_enrollments.drop();
db.assignments.drop();
db.submissions.drop();

print("Cleared existing collections");

// ── Teachers ──
const t1 = db.users.insertOne({
    name: "Prof. Ahmaed Khan",
    email: "ahmed@edunet.in",
    roll_no: null,
    password: "teacher123",
    role: "teacher",
    created_at: new Date(),
});

const t2 = db.users.insertOne({
    name: "Dr. Priya Sharma",
    email: "priya@edunet.in",
    roll_no: null,
    password: "teacher123",
    role: "teacher",
    created_at: new Date(),
});

const teacher1Id = t1.insertedId;
const teacher2Id = t2.insertedId;

print("Inserted teachers: " + teacher1Id + ", " + teacher2Id);

// ── Students ──
const students = [
    { name: "Aarav Mehta", roll_no: "21AIML101", password: "student123" },
    { name: "Sneha Patil", roll_no: "21AIML102", password: "student123" },
    { name: "Rohan Desai", roll_no: "21AIML103", password: "student123" },
    { name: "Prachi Joshi", roll_no: "21AIML104", password: "student123" },
    { name: "Kabir Singh", roll_no: "21CS201", password: "student123" },
    { name: "Tanvi Naik", roll_no: "21CS202", password: "student123" },
];

const studentIds = [];
for (const s of students) {
    const res = db.users.insertOne({
        name: s.name,
        email: s.roll_no.toLowerCase() + "@student.edunet.in",
        roll_no: s.roll_no,
        password: s.password,
        role: "student",
        created_at: new Date(),
    });
    studentIds.push(res.insertedId);
}

print("Inserted " + studentIds.length + " students");

// ── Subjects ──
const sub1 = db.subjects.insertOne({
    subject_name: "Data Structures & Algorithms",
    subject_code: "CS301",
    teacher_id: teacher1Id,
    is_active: true,
    created_at: new Date(),
});

const sub2 = db.subjects.insertOne({
    subject_name: "Python Programming",
    subject_code: "CS101",
    teacher_id: teacher1Id,
    is_active: true,
    created_at: new Date(),
});

const sub3 = db.subjects.insertOne({
    subject_name: "Computer Networks",
    subject_code: "CS401",
    teacher_id: teacher2Id,
    is_active: true,
    created_at: new Date(),
});

print("Inserted subjects: CS301, CS101, CS401");

// ── Enrollments ──
// AIML students → CS301 and CS101
// CS students → CS301 and CS401
const aimlStudents = studentIds.slice(0, 4); // 21AIML101–104
const csStudents = studentIds.slice(4, 6); // 21CS201–202

for (const sid of aimlStudents) {
    db.subject_enrollments.insertOne({ student_id: sid, subject_id: sub1.insertedId, enrolled_at: new Date() });
    db.subject_enrollments.insertOne({ student_id: sid, subject_id: sub2.insertedId, enrolled_at: new Date() });
}

for (const sid of csStudents) {
    db.subject_enrollments.insertOne({ student_id: sid, subject_id: sub1.insertedId, enrolled_at: new Date() });
    db.subject_enrollments.insertOne({ student_id: sid, subject_id: sub3.insertedId, enrolled_at: new Date() });
}

print("Inserted enrollments");

// ── Done ──
print("\n=== SEED COMPLETE ===");
print("Login credentials:");
print("  Staff:   email=ahmed@edunet.in     password=teacher123");
print("  Staff:   email=priya@edunet.in     password=teacher123");
print("  Student: roll_no=21AIML101  password=student123");
print("  Student: roll_no=21AIML102  password=student123");
print("  Student: roll_no=21CS201    password=student123");
print("Total documents inserted:");
print("  users:               " + db.users.countDocuments());
print("  subjects:            " + db.subjects.countDocuments());
print("  subject_enrollments: " + db.subject_enrollments.countDocuments());
