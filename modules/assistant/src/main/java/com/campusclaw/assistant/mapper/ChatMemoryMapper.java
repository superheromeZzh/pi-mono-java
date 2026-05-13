/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.mapper;

import java.util.List;

import com.campusclaw.assistant.memory.ChatMemoryEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper backing {@link com.campusclaw.assistant.memory.MyBatisChatMemoryRepository}.
 * Provides CRUD queries against the {@code chat_memory} table keyed by conversation id.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Mapper
public interface ChatMemoryMapper {

    @Select("SELECT id, conversation_id, role, content, sequence, created_at "
            + "FROM chat_memory WHERE conversation_id = #{conversationId} ORDER BY sequence")
    List<ChatMemoryEntity> selectByConversationId(String conversationId);

    @Insert("INSERT INTO chat_memory (conversation_id, role, content, sequence) "
            + "VALUES (#{conversationId}, #{role}, #{content}, #{sequence})")
    void insert(ChatMemoryEntity entity);

    @Delete("DELETE FROM chat_memory WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(String conversationId);
}
