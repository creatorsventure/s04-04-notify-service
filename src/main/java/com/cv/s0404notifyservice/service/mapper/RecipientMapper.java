package com.cv.s0404notifyservice.service.mapper;

import com.cv.s0402notifyservicepojo.dto.RecipientDto;
import com.cv.s0402notifyservicepojo.entity.Recipient;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecipientMapper {

    RecipientDto toDto(Recipient message);

    Recipient toEntity(RecipientDto dto);
}
