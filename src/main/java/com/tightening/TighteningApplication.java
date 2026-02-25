package com.tightening;

import com.tightening.entity.UserAccountInfo;
import com.tightening.service.UserAccountInfoService;
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

        UserAccountInfoService userAccountInfoService = run.getBean(UserAccountInfoService.class);
        userAccountInfoService.save(new UserAccountInfo()
                                            .setStaffId("S001")
                                            .setName("Test")
                                            .setAccount("test")
                                            .setUserId(1L)
                                            .setCreator("Sys")
                                            .setModifier("Sys"));
    }

}
