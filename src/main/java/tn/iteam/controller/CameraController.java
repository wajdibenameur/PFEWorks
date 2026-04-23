package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.service.CameraInventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraInventoryService cameraInventoryService;

    @GetMapping
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return cameraInventoryService.getRegisteredCameras();
    }
}
