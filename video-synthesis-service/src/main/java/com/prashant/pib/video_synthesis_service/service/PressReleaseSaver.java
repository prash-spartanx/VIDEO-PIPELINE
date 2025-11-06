package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import com.prashant.pib.video_synthesis_service.repository.PressReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PressReleaseSaver {

    private final PressReleaseRepository pressReleaseRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PressRelease saveNew(PressRelease pr) {
        return pressReleaseRepository.save(pr);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PressRelease saveUpdate(PressRelease pr) {
        return pressReleaseRepository.save(pr);
    }
}
