import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Terminal, User, GraduationCap, Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { apiLogin } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";

const Login = () => {
  const navigate = useNavigate();
  const { setUser } = useAuth();
  const { toast } = useToast();
  const [role, setRole] = useState<"staff" | "student">("staff");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!identifier.trim() || !password.trim()) {
      toast({ title: "Missing fields", description: "Please enter your credentials.", variant: "destructive" });
      return;
    }
    setLoading(true);
    try {
      const data = await apiLogin(identifier.trim(), password, role === "staff" ? "teacher" : "student");
      setUser({
        id: data.id,
        name: data.name,
        role: data.role === "teacher" ? "staff" : "student",
        roll_no: data.roll_no,
      }, rememberMe);
      if (data.role === "teacher") {
        navigate("/staff/dashboard");
      } else {
        navigate("/student/dashboard");
      }
    } catch (err: any) {
      toast({
        title: "Login failed",
        description: err.message || "Invalid credentials. Please try again.",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="w-full max-w-md space-y-8">
        {/* Logo */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary">
            <Terminal className="w-8 h-8 text-primary-foreground" />
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            EduNet
          </h1>
          <p className="text-muted-foreground text-sm">
            Lab Session Management System
          </p>
        </div>

        {/* Role Selector */}
        <div className="flex gap-2 p-1 bg-secondary rounded-lg">
          <button
            type="button"
            onClick={() => setRole("staff")}
            className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-md text-sm font-medium transition-all ${role === "staff"
              ? "bg-primary text-primary-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
              }`}
          >
            <User className="w-4 h-4" />
            Staff
          </button>
          <button
            type="button"
            onClick={() => setRole("student")}
            className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-md text-sm font-medium transition-all ${role === "student"
              ? "bg-primary text-primary-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
              }`}
          >
            <GraduationCap className="w-4 h-4" />
            Student
          </button>
        </div>

        {/* Login Form */}
        <Card className="border-border">
          <CardHeader className="pb-4">
            <CardTitle className="text-lg">
              {role === "staff" ? "Staff Login" : "Student Login"}
            </CardTitle>
            <CardDescription>
              {role === "staff"
                ? "Enter your Employee ID and password"
                : "Enter your Roll Number and password"}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleLogin} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="identifier">
                  {role === "staff" ? "Employee ID / Email" : "Roll Number"}
                </Label>
                <Input
                  id="identifier"
                  type="text"
                  placeholder={role === "staff" ? "e.g. EMP1001 or staff@edu.in" : "e.g. 21AIML101"}
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className="h-11"
                  disabled={loading}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="h-11"
                  disabled={loading}
                />
              </div>
              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="remember"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 text-primary focus:ring-primary"
                  disabled={loading}
                />
                <Label htmlFor="remember" className="text-sm font-normal cursor-pointer">Remember me</Label>
              </div>
              <Button type="submit" className="w-full h-11 text-sm font-semibold" disabled={loading}>
                {loading ? (
                  <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> Signing in...</>
                ) : "Sign In"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          LAN-only access · Same network required for lab sessions
        </p>
      </div>
    </div>
  );
};

export default Login;
