package com.ddockddack.domain.bestcut.service;

import com.ddockddack.domain.bestcut.entity.Bestcut;
import com.ddockddack.domain.bestcut.repository.BestcutRepository;
import com.ddockddack.domain.bestcut.request.BestcutImageReq;
import com.ddockddack.domain.bestcut.request.BestcutSaveReq;
import com.ddockddack.domain.member.entity.Member;
import com.ddockddack.domain.member.entity.Role;
import com.ddockddack.domain.member.repository.MemberRepository;
import com.ddockddack.global.error.ErrorCode;
import com.ddockddack.global.error.ErrorResponse;
import com.ddockddack.global.error.exception.AccessDeniedException;
import com.ddockddack.global.error.exception.NotFoundException;
import java.io.File;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BestcutService {

    private final BestcutRepository bestcutRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveBestcut(BestcutSaveReq saveReq) {

        Member member = memberRepository.findById(saveReq.getMemberId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        for (BestcutImageReq imageReq : saveReq.getImages()) {
            MultipartFile imageFile = imageReq.getBestcutImg();

            try {
                imageFile.transferTo(new File(imageFile.getOriginalFilename()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            Bestcut bestcut = Bestcut.builder()
                    .member(member)
                    .gameTitle(saveReq.getGameTitle())
                    .gameImageUrl(imageReq.getGameImgUrl())
                    .gameImgDesc(imageReq.getGameImgDesc())
                    .imageUrl(imageFile.getOriginalFilename())
                    .title(imageReq.getBestcutImgTitle())
                    .build();

            bestcutRepository.save(bestcut);
        }
    }

    /**
     * 삭제하려는 member의 id와 베스트컷이 참조하는 member의 id가 다르면 예외 발생
     * @param bestcutId
     * @param memberId
     */
    @Transactional
    public void removeBestcut(Long bestcutId, Long memberId) {
        Bestcut bestcut = bestcutRepository.findById(bestcutId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BESTCUT_NOT_FOUND));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getRole() != Role.ADMIN && bestcut.getMember().getId() != memberId) {
            throw new AccessDeniedException(ErrorCode.NOT_AUTHORIZED);
        }

        bestcutRepository.delete(bestcut);
    }

}
