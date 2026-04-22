package com.example.ticket_javara.global.event;

/**
 * 이벤트(공연/스포츠) 등록 완료 신호
 * EventService 발행 → EventCreatedEventListener 수신 → 검색 캐시 무효화
 */
public class EventCreatedEvent {
    // 페이로드 없음 — 발행 신호 역할만
    // 프로젝트 규모에 비해 오버스펙으로 판단 사용x 추후 스케일업 및 스터디를 위해 추가함
}
