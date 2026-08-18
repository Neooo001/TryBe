package com.trybe.material.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 素材校验 MQ 消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCheckMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 素材ID */
    private Long materialId;

    /** 消息ID（用于幂等追踪） */
    private String messageId;
}
