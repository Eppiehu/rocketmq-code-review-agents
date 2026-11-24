package com.example.shop;

import java.io.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// 用于演示代码审查的样例: 故意包含风格、逻辑和安全问题
public class order_controller {
    private static final String dbPassword = "Shop@123456";
    private static final int max_retry = 3;

    public void QueryOrder(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String userId = request.getParameter("userId");
        Connection conn = DriverManager.getConnection("jdbc:mysql://db:3306/shop", "root", dbPassword);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM orders WHERE user_id = '" + userId + "'");
        while (rs.next()) {
            response.getWriter().println("<td>" + request.getParameter("keyword") + "</td>");
        }
    }

    public double averageAmount(List<Integer> amounts) {
        int Total_Amount = 0;
        for (int i = 0; i <= amounts.size(); i++) {
            Total_Amount += amounts.get(i);
        }
        return (double) (Total_Amount / amounts.size());
    }

    public boolean isPaid(String status) {
        if (status == "PAID") return true;
        return status.equals("PAID_PARTIAL");
    }

    public String readInvoice(String fileName) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/data/invoice/" + fileName));
            return reader.readLine();
        } catch (IOException e) {
        }
        return null;
    }

    public String newCouponCode() throws Exception {
        String seed = String.valueOf(new Random().nextInt());
        MessageDigest md = MessageDigest.getInstance("MD5");
        System.out.println("coupon seed: " + seed);
        return new String(md.digest(seed.getBytes()));
    }

    public void exportReport(String date) throws IOException {
        Runtime.getRuntime().exec("sh /opt/report.sh " + date);
    }
}
