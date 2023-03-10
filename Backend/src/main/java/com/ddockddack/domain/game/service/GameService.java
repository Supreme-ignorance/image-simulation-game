package com.ddockddack.domain.game.service;

import com.ddockddack.domain.game.entity.Game;
import com.ddockddack.domain.game.entity.GameImage;
import com.ddockddack.domain.game.entity.StarredGame;
import com.ddockddack.domain.game.repository.GameImageRepository;
import com.ddockddack.domain.game.repository.GameRepository;
import com.ddockddack.domain.game.repository.GameRepositorySupport;
import com.ddockddack.domain.game.repository.StarredGameRepository;
import com.ddockddack.domain.game.request.GameImageModifyReq;
import com.ddockddack.domain.game.request.GameImageParam;
import com.ddockddack.domain.game.request.GameModifyReq;
import com.ddockddack.domain.game.request.GameSaveReq;
import com.ddockddack.domain.game.response.GameDetailRes;
import com.ddockddack.domain.game.response.GameRes;
import com.ddockddack.domain.game.response.ReportedGameRes;
import com.ddockddack.domain.game.response.StarredGameRes;
import com.ddockddack.domain.member.entity.Member;
import com.ddockddack.domain.member.entity.Role;
import com.ddockddack.domain.member.repository.MemberRepository;
import com.ddockddack.domain.report.entity.ReportType;
import com.ddockddack.domain.report.entity.ReportedGame;
import com.ddockddack.domain.report.repository.ReportedGameRepository;
import com.ddockddack.global.error.ErrorCode;
import com.ddockddack.global.error.exception.AccessDeniedException;
import com.ddockddack.global.error.exception.AlreadyExistResourceException;
import com.ddockddack.global.error.exception.ImageExtensionException;
import com.ddockddack.global.error.exception.NotFoundException;
import com.ddockddack.global.service.AwsS3Service;
import com.ddockddack.global.util.PageCondition;
import com.ddockddack.global.util.PageConditionReq;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final GameImageRepository gameImageRepository;
    private final MemberRepository memberRepository;
    private final StarredGameRepository starredGameRepository;
    private final ReportedGameRepository reportedGameRepository;
    private final GameRepositorySupport gameRepositorySupport;
    private final AwsS3Service awsS3Service;

    /**
     * ?????? ?????? ??????
     *
     * @param memberId
     * @param pageConditionReq
     * @return
     */
    @Transactional(readOnly = true)
    public PageImpl<GameRes> findAllGames(Long memberId, PageConditionReq pageConditionReq) {
        PageCondition pageCondition = pageConditionReq.toEntity();
        return gameRepositorySupport.findAllGameBySearch(memberId, pageCondition);
    }

    /**
     * ?????? ?????? ??????
     *
     * @param gameId
     * @return
     */
    @Transactional(readOnly = true)
    public GameDetailRes findGame(Long gameId) {
        List<GameDetailRes> result = gameRepositorySupport.findGame(gameId);
        if (result.size() == 0) {
            throw new NotFoundException(ErrorCode.GAME_NOT_FOUND);
        }
        return result.get(0);
    }

    /**
     * ?????? ??????
     *
     * @param gameSaveReq
     * @return gameId
     */
    public Long saveGame(Long memberId, GameSaveReq gameSaveReq) {

        // memberId??? member ??????. ?????? ????????? null ?????? NotFoundException ??????.
        Member getMember = memberRepository.findById(memberId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        // ?????? ??????
        Game game = Game
                .builder()
                .member(getMember)
                .title(gameSaveReq.getGameTitle())
                .category(gameSaveReq.getGameCategory())
                .description(gameSaveReq.getGameDesc())
                .build();

        Long gameId = gameRepository.save(game).getId();

        // ?????? ????????? ?????????

        List<GameImage> gameImages = new ArrayList<>();
        for (GameImageParam gameImageParam : gameSaveReq.getImages()) {
            String imageExtension; // ????????? ?????????
            String contentType = gameImageParam.getGameImage().getContentType();

            // ????????? ???????????? jpeg, png??? ????????? ????????? ???????????? ?????? ??????
            if (contentType.contains("image/jpeg")) {
                imageExtension = ".jpg";
            } else {
                throw new ImageExtensionException(ErrorCode.EXTENSION_NOT_ALLOWED);
            }
            // ?????? ?????????
            String fileName = awsS3Service.multipartFileUpload(gameImageParam.getGameImage());

            GameImage gameImage = GameImage.builder()
                    .game(game)
                    .imageUrl(fileName)
                    .description(gameImageParam.getGameImageDesc())
                    .build();
            // ???????????? ??????
            gameImages.add(gameImage);

        }

        // ????????? ?????? ?????? gameImage ?????? ?????? ??????
        gameImageRepository.saveAll(gameImages);

        return gameId;
    }

    /**
     * ?????? ??????
     *
     * @param memberId
     * @param gameModifyReq
     */
    public void modifyGame(Long memberId, GameModifyReq gameModifyReq) {
        // ??????
        checkAccessValidation(memberId, gameModifyReq.getGameId());

        Game getGame = gameRepository.getReferenceById(gameModifyReq.getGameId());

        // ?????? ??????, ?????? ??????
        getGame.updateGame(gameModifyReq.getGameTitle(), gameModifyReq.getGameDesc());

        List<String> tempImage = new ArrayList<>();
        for (GameImageModifyReq gameImageModifyReq : gameModifyReq.getImages()) {
            GameImage getGameImage = gameImageRepository.getReferenceById(gameImageModifyReq.getGameImageId());

            String imageExtension; // ????????? ?????????
            String contentType = gameImageModifyReq.getGameImage().getContentType();

            if (!contentType.contains("image/jpeg")) {
                throw new ImageExtensionException(ErrorCode.EXTENSION_NOT_ALLOWED);
            }
            String fileName = awsS3Service.multipartFileUpload(gameImageModifyReq.getGameImage());

            // ????????????
            getGameImage.updateGameImage(fileName, gameImageModifyReq.getGameImageDesc());

        }
    }

    /**
     * ?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    public void removeGame(Long memberId, Long gameId) {

        // ??????
        checkAccessValidation(memberId, gameId);
        gameImageRepository.deleteByGameId(gameId);
        starredGameRepository.deleteByGameId(gameId);
        reportedGameRepository.deleteByGameId(gameId);
        gameRepository.deleteById(gameId);

    }

    /**
     * ?????? ?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    public void starredGame(Long memberId, Long gameId) {

        // ??????
        checkMemberAndGameValidation(memberId, gameId);

        boolean isExist = starredGameRepository.existsByMemberIdAndGameId(memberId, gameId);

        if (isExist) {
            throw new AlreadyExistResourceException(ErrorCode.ALREADY_EXIST_STTAREDGAME);
        }

        Member getMember = memberRepository.getReferenceById(memberId);
        Game getGame = gameRepository.getReferenceById(gameId);

        StarredGame starredGame = StarredGame.builder()
                .game(getGame)
                .member(getMember)
                .build();

        starredGameRepository.save(starredGame);
    }

    /**
     * ?????? ?????? ?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    public void unStarredGame(Long memberId, Long gameId) {

        // ??????
        checkMemberAndGameValidation(memberId, gameId);

        StarredGame getStarredGame = starredGameRepository.findByMemberIdAndGameId(memberId, gameId).orElseThrow(() ->
                new NotFoundException(ErrorCode.STARREDGAME_NOT_FOUND));

        starredGameRepository.delete(getStarredGame);
    }

    /**
     * ?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    public void reportGame(Long memberId, Long gameId, ReportType reportType) {

        // ??????
        checkMemberAndGameValidation(memberId, gameId);

        // ?????? ??????????????? ??????
        boolean isExist = reportedGameRepository.existsByReportMemberIdAndGameId(memberId, gameId);

        if (isExist) {
            throw new AlreadyExistResourceException(ErrorCode.ALREADY_EXIST_REPORTEDGAME);
        }

        Member reportMember = memberRepository.getReferenceById(memberId);
        Game getGame = gameRepository.getReferenceById(gameId);

        ReportedGame reportedGame = ReportedGame.builder()
                .game(getGame)
                .reportMember(reportMember)
                .reportedMember(getGame.getMember())
                .reportType(reportType)
                .build();

        reportedGameRepository.save(reportedGame);
    }

    /**
     * ?????? ?????? ?????? ?????? ??????
     *
     * @param memberId
     * @return
     */
    @Transactional(readOnly = true)
    public PageImpl<GameRes> findAllGameByMemberId(Long memberId, PageConditionReq pageConditionReq) {
        PageCondition pageCondition = pageConditionReq.toEntity();
        memberRepository.findById(memberId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));
        return gameRepositorySupport.findAllByMemberId(memberId, pageCondition);
    }

    /**
     * ?????? ?????? ????????? ?????? ?????? ??????
     *
     * @param memberId
     * @return
     */
    @Transactional(readOnly = true)
    public List<StarredGameRes> findAllStarredGames(Long memberId) {
        memberRepository.findById(memberId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));
        return gameRepositorySupport.findAllStarredGame(memberId);
    }

    /**
     * ?????? ??? ?????? ?????? ?????? ????????????
     *
     * @return
     */
    @Transactional(readOnly = true)
    public List<ReportedGameRes> findAllReportedGames() {

        return gameRepositorySupport.findAllReportedGame();
    }

    /**
     * ?????? ??????, ?????? ?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    private void checkAccessValidation(Long memberId, Long gameId) {

        // ???????????? ???????????? ??????
        Member getMember = memberRepository.findById(memberId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        // ???????????? ?????? ?????? ??????
        Game getGame = gameRepository.findById(gameId).orElseThrow(() ->
                new NotFoundException(ErrorCode.GAME_NOT_FOUND));

        // ???????????? ?????? ??????
        if (getMember.getRole().equals(Role.ADMIN)) {
            return;
        }

        // ?????? ????????? ?????? ???????????? ??????
        if ((memberId != getGame.getMember().getId())) {
            throw new AccessDeniedException(ErrorCode.NOT_AUTHORIZED);
        }
    }

    /**
     * ?????? ??????,?????? ??????
     *
     * @param memberId
     * @param gameId
     */
    private void checkMemberAndGameValidation(Long memberId, Long gameId) {
        // ???????????? ???????????? ??????
        memberRepository.findById(memberId).orElseThrow(() ->
                new NotFoundException(ErrorCode.MEMBER_NOT_FOUND));

        // ???????????? ?????? ?????? ??????
        gameRepository.findById(gameId).orElseThrow(() ->
                new NotFoundException(ErrorCode.GAME_NOT_FOUND));
    }


    /**
     * ?????? ????????? ?????? ?????? ??? ????????? ????????? ????????? ?????? ??????
     *
     * @param path
     * @param list
     */
    private void deleteImageFile(String path, List<String> list) {

        String absolutePath = new File("").getAbsolutePath() + File.separator;
        if (list.size() != 0) {
            for (int i = 0; i < list.size(); i++) {
                new File(absolutePath + path + File.separator + list.get(i)).delete();
            }
        }

    }

    /**
     * ?????? ?????? ????????? ????????? ???????????? ??????
     *
     * @param path
     */
    private void deleteDirectory(String path) {

        try {
            FileSystemUtils.deleteRecursively(Paths.get(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
