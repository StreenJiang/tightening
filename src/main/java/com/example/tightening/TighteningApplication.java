package com.example.tightening;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootApplication
public class TighteningApplication {

	public static void main(String[] args) {
        ConfigurableApplicationContext run = SpringApplication.run(TighteningApplication.class, args);

        DataSource dataSource = run.getBean(DataSource.class);

        try (Connection conn = dataSource.getConnection()) {
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("select * from person");
            while (resultSet.next()) {
                System.out.println(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
