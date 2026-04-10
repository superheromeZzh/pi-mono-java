package com.campusclaw.assistant.mapper;

import com.campusclaw.assistant.memory.ChatMemoryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
