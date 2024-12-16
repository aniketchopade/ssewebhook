package com.example.demo;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/")
public class HelloWorldServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.getWriter().write("<html><body>");
        resp.getWriter().write("<h1>Hello World</h1>");
        resp.getWriter().write("<input type='text' id='textbox' />");
        resp.getWriter().write("<button onclick='alert(document.getElementById(\"textbox\").value)'>Click Me</button>");
        resp.getWriter().write("</body></html>");
    }
}
