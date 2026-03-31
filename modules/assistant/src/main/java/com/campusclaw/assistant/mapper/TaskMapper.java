package com.campusclaw.assistant.mapper;

import com.campusclaw.assistant.task.RecurringTaskEntity;
import com.campusclaw.assistant.task.TaskEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TaskMapper {
    @Select("SELECT * FROM task WHERE id = #{id}")
    TaskEntity findById(String id);

    @Insert("INSERT INTO task (id, conversation_id, prompt, status, result, channel_name, created_at, updated_at) "
            + "VALUES (#{id}, #{conversationId}, #{prompt}, #{status}, #{result}, #{channelName}, #{createdAt}, #{updatedAt})")
    void insert(TaskEntity entity);

    @Update("UPDATE task SET conversation_id=#{conversationId}, prompt=#{prompt}, status=#{status}, "
            + "result=#{result}, channel_name=#{channelName}, updated_at=#{updatedAt} WHERE id=#{id}")
    void update(TaskEntity entity);

    @Select("SELECT * FROM task WHERE status = #{status}")
    List<TaskEntity> findByStatus(String status);

    @Select("SELECT * FROM task")
    List<TaskEntity> findAll();

    @Select("SELECT * FROM recurring_task")
    List<RecurringTaskEntity> findAllRecurring();

    @Insert("INSERT INTO recurring_task (id, name, description, cron_expression, prompt, model_id) "
            + "VALUES (#{id}, #{name}, #{description}, #{cronExpression}, #{prompt}, #{modelId})")
    void insertRecurring(RecurringTaskEntity entity);

    @Delete("DELETE FROM recurring_task WHERE id = #{id}")
    void deleteRecurring(String id);

    @Delete("DELETE FROM task WHERE id = #{id}")
    void deleteTask(String id);

    @Select("SELECT * FROM recurring_task WHERE id = #{id}")
    RecurringTaskEntity findRecurringById(String id);

    @Update("UPDATE recurring_task SET last_status = #{lastStatus}, "
            + "last_execution_at = #{lastExecutionAt}, "
            + "execution_results = #{executionResults} WHERE id = #{id}")
    void updateRecurring(RecurringTaskEntity entity);

    @Delete("DELETE FROM task WHERE conversation_id LIKE CONCAT('recurring-', #{recurringId}, '-%')")
    void deleteRecurringTaskExecutions(String recurringId);

    @Delete("DELETE FROM chat_memory WHERE conversation_id LIKE CONCAT('recurring-', #{recurringId}, '-%')")
    void deleteRecurringChatMemory(String recurringId);
}
