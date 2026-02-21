import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "@/lib/auth";
import Login from "./pages/Login";
import StaffDashboard from "./pages/staff/StaffDashboard";
import SubjectDetail from "./pages/staff/SubjectDetail";
import StudentDashboard from "./pages/student/StudentDashboard";
import AssignmentView from "./pages/student/AssignmentView";
import ChatPage from "./pages/ChatPage";
import NotFound from "./pages/NotFound";

const queryClient = new QueryClient();

function StaffGuard({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/" replace />;
  if (user.role !== "staff") return <Navigate to="/student/dashboard" replace />;
  return <>{children}</>;
}

function StudentGuard({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/" replace />;
  if (user.role !== "student") return <Navigate to="/staff/dashboard" replace />;
  return <>{children}</>;
}

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <AuthProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Login />} />
            <Route path="/staff/dashboard" element={<StaffGuard><StaffDashboard /></StaffGuard>} />
            <Route path="/staff/subject/:id" element={<StaffGuard><SubjectDetail /></StaffGuard>} />
            <Route path="/student/dashboard" element={<StudentGuard><StudentDashboard /></StudentGuard>} />
            <Route path="/student/assignment/:id" element={<StudentGuard><AssignmentView /></StudentGuard>} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
