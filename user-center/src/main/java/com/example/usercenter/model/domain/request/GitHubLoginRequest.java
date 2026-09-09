package com.example.usercenter.model.domain.request;

import lombok.Data;
import java.io.Serializable;

@Data
public class GitHubLoginRequest implements Serializable {
    private String code;
}
