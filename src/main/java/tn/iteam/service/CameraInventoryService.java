package tn.iteam.service;

import tn.iteam.dto.CameraDeviceDTO;

import java.util.List;

public interface CameraInventoryService {

    List<CameraDeviceDTO> getRegisteredCameras();
}
