import java.sql.*;

public class Promote {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/yiya?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection conn = DriverManager.getConnection(url, "root", "123456");
             Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE user SET user_role = 1 WHERE user_account = 'avatartest1'");
            System.out.println("avatartest1 已临时提权为管理员");
        }
    }
}
