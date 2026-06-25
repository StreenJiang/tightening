package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.mapper.BoltPartsBarcodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BoltPartsBarcodeService extends ServiceImpl<BoltPartsBarcodeMapper, BoltPartsBarcode> {
}
