package com.ddockddack.domain.bestcut.repository;

import com.ddockddack.domain.bestcut.response.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ddockddack.domain.member.entity.QMember.member;
import static com.ddockddack.domain.report.entity.QReportedBestcut.reportedBestcut;

@Repository
@RequiredArgsConstructor
public class BestcutRepositorySupport {
    private final JPAQueryFactory jpaQueryFactory;

    // 신고된 베스트컷 목록 조회
    public List<ReportedBestcutRes> findAllReportedBestcut() {
        return jpaQueryFactory
                .select(new QReportedBestcutRes(
                        reportedBestcut.id.as("reportId"),
                        reportedBestcut.reportMember.id.as("reportMemberId"),
                        reportedBestcut.reportedMember.id.as("reportedMemberId"),
                        reportedBestcut.bestcut.id.as("gameId"),
                        reportedBestcut.reportType.as("reason").stringValue()
                ))
                .from(reportedBestcut)
                .innerJoin(reportedBestcut.reportMember, member)
                .innerJoin(reportedBestcut.reportedMember, member)
                .orderBy(reportedBestcut.id.desc())
                .fetch();
    }
}
