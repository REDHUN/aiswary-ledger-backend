package com.redhun.aiswarya_ledger_api.service;

import com.redhun.aiswarya_ledger_api.domain.entity.SpecialLoanType;
import com.redhun.aiswarya_ledger_api.dto.request.CreateSpecialLoanTypeRequest;
import com.redhun.aiswarya_ledger_api.dto.response.SpecialLoanTypeDto;
import com.redhun.aiswarya_ledger_api.exception.BusinessException;
import com.redhun.aiswarya_ledger_api.exception.ResourceNotFoundException;
import com.redhun.aiswarya_ledger_api.repository.SpecialLoanTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecialLoanTypeService {

    private final SpecialLoanTypeRepository specialLoanTypeRepository;

    @Transactional(readOnly = true)
    public List<SpecialLoanTypeDto> getAllSpecialLoanTypes() {
        return specialLoanTypeRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialLoanTypeDto> getActiveSpecialLoanTypes() {
        return specialLoanTypeRepository.findByIsActiveTrue().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public SpecialLoanTypeDto createSpecialLoanType(CreateSpecialLoanTypeRequest request) {
        if (specialLoanTypeRepository.findByNameIgnoreCase(request.getName().trim()).isPresent()) {
            throw new BusinessException("SPECIAL_LOAN_TYPE_EXISTS", "Special loan type with name '" + request.getName() + "' already exists");
        }

        SpecialLoanType type = SpecialLoanType.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        return mapToDto(specialLoanTypeRepository.save(type));
    }

    @Transactional
    public SpecialLoanTypeDto updateSpecialLoanType(Long id, CreateSpecialLoanTypeRequest request) {
        SpecialLoanType type = specialLoanTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SpecialLoanType", "id", id));

        specialLoanTypeRepository.findByNameIgnoreCase(request.getName().trim())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new BusinessException("SPECIAL_LOAN_TYPE_EXISTS", "Special loan type with name '" + request.getName() + "' already exists");
                    }
                });

        type.setName(request.getName().trim());
        type.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            type.setIsActive(request.getIsActive());
        }

        return mapToDto(specialLoanTypeRepository.save(type));
    }

    private SpecialLoanTypeDto mapToDto(SpecialLoanType type) {
        return SpecialLoanTypeDto.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .isActive(type.getIsActive())
                .createdAt(type.getCreatedAt())
                .build();
    }
}
