package com.ctrip.zeus.restful.resource;

import com.ctrip.zeus.auth.Authorize;
import com.ctrip.zeus.exceptions.ValidationException;
import com.ctrip.zeus.executor.TaskManager;
import com.ctrip.zeus.model.entity.Group;
import com.ctrip.zeus.model.entity.GroupVirtualServer;
import com.ctrip.zeus.model.entity.VirtualServer;
import com.ctrip.zeus.restful.message.ResponseHandler;
import com.ctrip.zeus.service.model.*;
import com.ctrip.zeus.service.query.GroupCriteriaQuery;
import com.ctrip.zeus.service.task.constant.TaskOpsType;
import com.ctrip.zeus.tag.PropertyBox;
import com.ctrip.zeus.task.entity.OpsTask;
import com.ctrip.zeus.task.entity.TaskResult;
import com.ctrip.zeus.task.entity.TaskResultList;
import com.google.common.base.Joiner;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Created by fanqq on 2015/6/11.
 */

@Component
@Path("/deactivate")
public class DeactivateResource {
    @Resource
    private PropertyBox propertyBox;
    @Resource
    private SlbRepository slbRepository;
    @Resource
    private ResponseHandler responseHandler;
    @Resource
    private TaskManager taskManager;
    @Resource
    private EntityFactory entityFactory;
    @Resource
    private GroupCriteriaQuery groupCriteriaQuery;

    private static DynamicLongProperty apiTimeout = DynamicPropertyFactory.getInstance().getLongProperty("api.timeout", 15000L);

    @GET
    @Path("/group")
    @Authorize(name = "deactivate")
    public Response deactivateGroup(@Context HttpServletRequest request, @Context HttpHeaders hh, @QueryParam("groupId") List<Long> groupIds, @QueryParam("groupName") List<String> groupNames) throws Exception {
        Set<Long> _groupIds = new HashSet<>();

        if (groupIds != null && !groupIds.isEmpty()) {
            _groupIds.addAll(groupIds);
        }
        if (groupNames != null && !groupNames.isEmpty()) {
            for (String groupName : groupNames) {
                Long groupId = groupCriteriaQuery.queryByName(groupName);
                if (groupId != null && !groupId.equals(0L)) {
                    _groupIds.add(groupId);
                }
            }
        }

        ModelStatusMapping<Group> groupMap = entityFactory.getGroupsByIds(_groupIds.toArray(new Long[]{}));

        _groupIds.removeAll(groupMap.getOnlineMapping().keySet());
        if (_groupIds.size() > 0) {
            throw new ValidationException("Groups with id (" + Joiner.on(",").join(_groupIds) + ") are not activated.");
        }

        Set<Long> vsIds = new HashSet<>();
        for (Map.Entry<Long, Group> e : groupMap.getOnlineMapping().entrySet()) {
            Group group = e.getValue();
            if (group == null) {
                throw new Exception("Unexpected online group with null value. groupId=" + e.getKey() + ".");
            }
            for (GroupVirtualServer vs : group.getGroupVirtualServers()) {
                vsIds.add(vs.getVirtualServer().getId());
            }
        }
        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesByIds(vsIds.toArray(new Long[]{}));

        List<OpsTask> tasks = new ArrayList<>();
        for (Map.Entry<Long, Group> e : groupMap.getOnlineMapping().entrySet()) {
            Group group = e.getValue();

            Set<Long> slbIds = new HashSet<>();
            for (GroupVirtualServer gvs : group.getGroupVirtualServers()) {
                VirtualServer vs = vsMap.getOnlineMapping().get(gvs.getVirtualServer().getId());
                if (vs == null) {
                    throw new ValidationException("Virtual server " + gvs.getVirtualServer().getId() + " is found deactivated.");
                }
                slbIds.addAll(vs.getSlbIds());
            }

            for (Long slbId : slbIds) {
                OpsTask task = new OpsTask();
                task.setGroupId(e.getKey());
                task.setOpsType(TaskOpsType.DEACTIVATE_GROUP);
                task.setTargetSlbId(slbId);
                tasks.add(task);
            }
        }
        List<Long> taskIds = taskManager.addTask(tasks);

        List<TaskResult> results = taskManager.getResult(taskIds, apiTimeout.get());

        TaskResultList resultList = new TaskResultList();
        for (TaskResult t : results) {
            resultList.addTaskResult(t);
        }
        resultList.setTotal(results.size());

        try {
            propertyBox.set("status", "deactivated", "group", groupMap.getOnlineMapping().keySet().toArray(new Long[groupMap.getOnlineMapping().size()]));
        } catch (Exception ex) {
        }
        return responseHandler.handle(resultList, hh.getMediaType());
    }

    @GET
    @Path("/vs")
    @Authorize(name = "activate")
    public Response deactivateVirtualServer(@Context HttpServletRequest request,
                                            @Context HttpHeaders hh,
                                            @QueryParam("vsId") Long vsId) throws Exception {
        Set<IdVersion> relatedGroupIds = groupCriteriaQuery.queryByVsId(vsId);
        if (relatedGroupIds.size() > 0) {
            Set<Long> groupIds = new HashSet<>();
            for (IdVersion key : relatedGroupIds) {
                groupIds.add(key.getId());
            }
            relatedGroupIds.retainAll(groupCriteriaQuery.queryByIdsAndMode(groupIds.toArray(new Long[groupIds.size()]), SelectionMode.ONLINE_EXCLUSIVE));
            throw new ValidationException("Activated groups are found related to Vs[" + vsId + "].");
        }

        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesByIds(new Long[]{vsId});
        if (vsMap.getOnlineMapping() == null || vsMap.getOnlineMapping().get(vsId) == null) {
            throw new ValidationException("Vs is not activated. VsId:" + vsId);
        }

        VirtualServer vs = vsMap.getOnlineMapping().get(vsId);
        List<OpsTask> deactivatingTask = new ArrayList<>();
        for (Long slbId : vs.getSlbIds()) {
            OpsTask task = new OpsTask();
            task.setSlbVirtualServerId(vsId);
            task.setCreateTime(new Date());
            task.setOpsType(TaskOpsType.DEACTIVATE_VS);
            task.setTargetSlbId(slbId);
        }
        List<Long> taskIds = taskManager.addTask(deactivatingTask);

        List<TaskResult> results = taskManager.getResult(taskIds, apiTimeout.get());

        TaskResultList resultList = new TaskResultList();
        for (TaskResult t : results) {
            resultList.addTaskResult(t);
        }
        resultList.setTotal(results.size());

        try {
            propertyBox.set("status", "deactivated", "vs", vsId);
        } catch (Exception ex) {
        }
        return responseHandler.handle(resultList, hh.getMediaType());
    }

    @GET
    @Path("/soft/group")
    @Authorize(name = "activate")
    public Response softDeactivateGroup(@Context HttpServletRequest request,
                                        @Context HttpHeaders hh,
                                        @QueryParam("vsId") Long vsId,
                                        @QueryParam("groupId") Long groupId) throws Exception {
        IdVersion[] groupIdsOffline = groupCriteriaQuery.queryByIdAndMode(groupId, SelectionMode.OFFLINE_FIRST);
        if (groupIdsOffline.length == 0) {
            throw new ValidationException("Cannot find group by groupId-" + groupId + ".");
        }
        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesByIds(new Long[]{vsId});
        if (vsMap.getOnlineMapping() == null || vsMap.getOnlineMapping().get(vsId) == null) {
            throw new ValidationException("Vs is not activated.VsId:" + vsId);
        }
        VirtualServer vs = vsMap.getOnlineMapping().get(vsId);
        List<OpsTask> softDeactivatingTasks = new ArrayList<>();
        for (Long slbId : vs.getSlbIds()) {
            OpsTask task = new OpsTask();
            task.setSlbVirtualServerId(vsId);
            task.setCreateTime(new Date());
            task.setOpsType(TaskOpsType.SOFT_DEACTIVATE_GROUP);
            task.setTargetSlbId(slbId);
            task.setGroupId(groupId);
            task.setVersion(groupIdsOffline[0].getVersion());
            softDeactivatingTasks.add(task);
        }
        List<Long> taskIds = taskManager.addTask(softDeactivatingTasks);

        List<TaskResult> results = taskManager.getResult(taskIds, apiTimeout.get());

        TaskResultList resultList = new TaskResultList();
        for (TaskResult t : results) {
            resultList.addTaskResult(t);
        }
        resultList.setTotal(results.size());

        return responseHandler.handle(resultList, hh.getMediaType());
    }

    @GET
    @Path("/slb")
    @Authorize(name = "activate")
    public Response deactivateSlb(@Context HttpServletRequest request,
                                  @Context HttpHeaders hh,
                                  @QueryParam("slbId") Long slbId) throws Exception {
        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesBySlbIds(slbId);
        if (vsMap.getOnlineMapping() != null && vsMap.getOnlineMapping().size() > 0) {
            throw new ValidationException("Has Activated Vses Related to Slb[" + slbId + "]");
        }
        IdVersion idVersion = new IdVersion(slbId, 0);
        slbRepository.updateStatus(new IdVersion[]{idVersion});

        try {
            propertyBox.set("status", "deactivated", "slb", slbId);
        } catch (Exception ex) {
        }
        return responseHandler.handle(slbRepository.getById(slbId), hh.getMediaType());
    }
}
