package tn.iteam.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ServiceStatusDTO {

    private String source;
    private String name;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
}

