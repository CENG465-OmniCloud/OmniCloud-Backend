package com.omnicloud.api.dto;

import lombok.Data;

@Data
public class ProviderRequest {
    private String name;
    private String type;
    private String endpointUrl;
    private String region;
    private String bucketName;
    private String accessKey;
    private String secretKey;
}