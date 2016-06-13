package com.ctrip.zeus.service.status.impl;

import com.ctrip.zeus.dal.core.ConfSlbActiveDao;
import com.ctrip.zeus.model.entity.*;
import com.ctrip.zeus.service.model.*;
import com.ctrip.zeus.service.query.GroupCriteriaQuery;
import com.ctrip.zeus.service.query.SlbCriteriaQuery;
import com.ctrip.zeus.service.query.VirtualServerCriteriaQuery;
import com.ctrip.zeus.service.status.GroupStatusService;
import com.ctrip.zeus.service.status.HealthCheckStatusService;
import com.ctrip.zeus.service.status.StatusOffset;
import com.ctrip.zeus.service.status.StatusService;
import com.ctrip.zeus.status.entity.GroupServerStatus;
import com.ctrip.zeus.status.entity.GroupStatus;
import com.ctrip.zeus.util.AssertUtils;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * User: mag
 * Date: 4/1/2015
 * Time: 2:26 PM
 */
@Service("groupStatusService")
public class GroupStatusServiceImpl implements GroupStatusService {
    private static DynamicIntProperty nginxStatusPort = DynamicPropertyFactory.getInstance().getIntProperty("slb.nginx.status-port", 10001);
    private static DynamicIntProperty adminServerPort = DynamicPropertyFactory.getInstance().getIntProperty("server.port", 8099);

    @Resource
    SlbRepository slbRepository;
    @Resource
    SlbCriteriaQuery slbCriteriaQuery;
    @Resource
    GroupCriteriaQuery groupCriteriaQuery;
    @Resource
    VirtualServerCriteriaQuery virtualServerCriteriaQuery;
    @Resource
    GroupRepository groupRepository;
    @Resource
    StatusService statusService;
    @Resource
    ConfSlbActiveDao confSlbActiveDao;
    @Resource
    EntityFactory entityFactory;
    @Resource
    private HealthCheckStatusService healthCheckStatusService;

    private Logger LOGGER = LoggerFactory.getLogger(GroupStatusServiceImpl.class);

    @Override
    public List<GroupStatus> getAllOnlineGroupsStatus() throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Set<IdVersion> slbIds = slbCriteriaQuery.queryAll(SelectionMode.ONLINE_EXCLUSIVE);
        for (IdVersion slb : slbIds) {
            result.addAll(getOnlineGroupsStatusBySlbId(slb.getId()));
        }
        return result;
    }

    @Override
    public List<GroupStatus> getAllOfflineGroupsStatus() throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Set<IdVersion> slbIds = slbCriteriaQuery.queryAll(SelectionMode.OFFLINE_FIRST);
        for (IdVersion slb : slbIds) {
            result.addAll(getOfflineGroupsStatusBySlbId(slb.getId()));
        }
        return result;
    }

    @Override
    public List<GroupStatus> getOnlineGroupsStatusBySlbId(Long slbId) throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Long[] vses = entityFactory.getVsIdsBySlbId(slbId, SelectionMode.ONLINE_EXCLUSIVE);
        ModelStatusMapping<Group> groups = entityFactory.getGroupsByVsIds(vses);
        if (groups.getOnlineMapping() == null || groups.getOnlineMapping().size() == 0) {
            return result;
        }
        result = getOnlineGroupsStatus(groups.getOnlineMapping(), slbId);
        return result;
    }

    @Override
    public List<GroupStatus> getOfflineGroupsStatusBySlbId(Long slbId) throws Exception {
        List<GroupStatus> result = new ArrayList<>();
        Long[] vses = entityFactory.getVsIdsBySlbId(slbId, SelectionMode.OFFLINE_FIRST);
        ModelStatusMapping<Group> groups = entityFactory.getGroupsByVsIds(vses);
        if (groups.getOfflineMapping() == null || groups.getOfflineMapping().size() == 0) {
            return result;
        }
        result = getOfflineGroupsStatus(groups.getOfflineMapping(), groups.getOnlineMapping(), slbId);
        return result;
    }

    @Override
    public GroupStatus getOnlineGroupStatus(Long groupId) throws Exception {
        GroupStatus result = null;
        ModelStatusMapping<Group> map = entityFactory.getGroupsByIds(new Long[]{groupId});
        if (map.getOnlineMapping() == null || map.getOnlineMapping().size() == 0) {
            return result;
        }
        List<Long> vsId = new ArrayList<>();
        for (GroupVirtualServer gvs : map.getOnlineMapping().get(groupId).getGroupVirtualServers()) {
            vsId.add(gvs.getVirtualServer().getId());
        }
        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesByIds(vsId.toArray(new Long[]{}));
        if (vsMap.getOnlineMapping() == null || vsMap.getOnlineMapping().size() == 0) {
            return result;
        }
        Long slbId = null;
        for (Long vid : vsMap.getOnlineMapping().keySet()) {
            slbId = vsMap.getOnlineMapping().get(vid).getSlbId();
            if (slbId != null)
                break;
        }
        List<GroupStatus> list = getOnlineGroupsStatus(map.getOnlineMapping(), slbId);
        if (list.size() > 0) {
            result = list.get(0);
        }
        return result;
    }

    @Override
    public GroupStatus getOfflineGroupStatus(Long groupId) throws Exception {
        GroupStatus result = null;
        ModelStatusMapping<Group> map = entityFactory.getGroupsByIds(new Long[]{groupId});
        if (map.getOfflineMapping() == null || map.getOfflineMapping().size() == 0) {
            return result;
        }
        List<Long> vsId = new ArrayList<>();
        for (GroupVirtualServer gvs : map.getOfflineMapping().get(groupId).getGroupVirtualServers()) {
            vsId.add(gvs.getVirtualServer().getId());
        }
        ModelStatusMapping<VirtualServer> vsMap = entityFactory.getVsesByIds(vsId.toArray(new Long[]{}));
        if (vsMap.getOfflineMapping() == null || vsMap.getOfflineMapping().size() == 0) {
            return result;
        }
        Long slbId = null;
        for (Long vid : vsMap.getOfflineMapping().keySet()) {
            slbId = vsMap.getOfflineMapping().get(vid).getSlbId();
            if (slbId != null)
                break;
        }
        List<GroupStatus> list = getOfflineGroupsStatus(map.getOfflineMapping(), map.getOnlineMapping(), slbId);
        if (list.size() > 0) {
            result = list.get(0);
        }
        return result;
    }

    @Override
    public GroupStatus getOfflineGroupStatus(Long groupId, Long slbId) throws Exception {
        GroupStatus result = null;
        ModelStatusMapping<Group> map = entityFactory.getGroupsByIds(new Long[]{groupId});
        if (map.getOfflineMapping() == null || map.getOfflineMapping().size() == 0) {
            return result;
        }
        List<GroupStatus> list = getOfflineGroupsStatus(map.getOfflineMapping(), map.getOnlineMapping(), slbId);
        if (list.size() > 0) {
            result = list.get(0);
        }
        return result;
    }

    @Override
    public List<GroupStatus> getOnlineGroupsStatus(Map<Long, Group> groups, Long slbId) throws Exception {
        List<GroupStatus> res = new ArrayList<>();
        GroupStatus status = null;

        Map<String, List<Boolean>> memberStatus = statusService.fetchGroupServerStatus(groups.keySet().toArray(new Long[]{}));
        Set<String> allDownServers = statusService.findAllDownServers();
        for (Group group : groups.values()) {
            Long groupId = group.getId();
            status = new GroupStatus();
            status.setGroupId(groupId);
            status.setSlbId(slbId);
            status.setGroupName(group.getName());
            status.setActivated(true);

            List<GroupServer> groupServerList = group.getGroupServers();//groupRepository.listGroupServersByGroup(groupId);
            for (GroupServer gs : groupServerList) {
                GroupServerStatus groupServerStatus = new GroupServerStatus();
                groupServerStatus.setIp(gs.getIp());
                groupServerStatus.setPort(gs.getPort());
                String key = groupId + "_" + gs.getIp();
                if (memberStatus.get(key) == null) {
                    LOGGER.error("[StatusError]Group Member Status is missing. groupId:" + groupId + "ip:" + gs.getIp());
                    continue;
                }
                boolean memberUp = memberStatus.get(key).get(StatusOffset.MEMBER_OPS);
                boolean serverUp = !allDownServers.contains(gs.getIp());
                boolean pullIn = memberStatus.get(key).get(StatusOffset.PULL_OPS);
                boolean raise = memberStatus.get(key).get(StatusOffset.HEALTHY);
                boolean up = false;
                if (memberUp && serverUp && pullIn) {
                    up = memberStatus.get(key).get(StatusOffset.HEALTH_CHECK);
                } else {
                    up = false;
                }
                groupServerStatus.setServer(serverUp);
                groupServerStatus.setMember(memberUp);
                groupServerStatus.setPull(pullIn);
                groupServerStatus.setHealthy(raise);
                groupServerStatus.setUp(up);
                status.addGroupServerStatus(groupServerStatus);
            }
            res.add(status);
        }
        return res;
    }

    @Override
    public List<GroupStatus> getOfflineGroupsStatus(Map<Long, Group> groups, Map<Long, Group> onlineGroups, Long slbId) throws Exception {
        List<GroupStatus> res = new ArrayList<>();
        GroupStatus status = null;
        Map<String, List<Boolean>> memberStatus = statusService.fetchGroupServerStatus(groups.keySet().toArray(new Long[]{}));
        Set<String> allDownServers = statusService.findAllDownServers();

        for (Group group : groups.values()) {
            Long groupId = group.getId();
            status = new GroupStatus();
            status.setGroupId(groupId);
            status.setSlbId(slbId);
            status.setGroupName(group.getName());
            status.setActivated(onlineGroups.containsKey(groupId));

            Group onlineGroup = onlineGroups.get(groupId);
            Set<String> onlineMembers = new HashSet<>();
            if (onlineGroup != null) {
                for (GroupServer groupServer : onlineGroup.getGroupServers()) {
                    onlineMembers.add(groupServer.getIp());
                }
            }

            List<GroupServer> groupServerList = group.getGroupServers();
            for (GroupServer gs : groupServerList) {
                GroupServerStatus groupServerStatus = new GroupServerStatus();
                groupServerStatus.setIp(gs.getIp());
                groupServerStatus.setPort(gs.getPort());
                String key = groupId + "_" + gs.getIp();
                if (memberStatus.get(key) == null) {
                    LOGGER.error("[StatusError]Group Member Status is missing. groupId:" + groupId + "ip:" + gs.getIp());
                    continue;
                }
                boolean memberUp = memberStatus.get(key).get(StatusOffset.MEMBER_OPS);
                boolean serverUp = !allDownServers.contains(gs.getIp());
                boolean pullIn = memberStatus.get(key).get(StatusOffset.PULL_OPS);
                boolean raise = memberStatus.get(key).get(StatusOffset.HEALTHY);
                boolean up = false;
                if (memberUp && serverUp && pullIn) {
                    up = memberStatus.get(key).get(StatusOffset.HEALTH_CHECK);
                } else {
                    up = false;
                }
                boolean online = onlineMembers.contains(gs.getIp());
                if (!online) {
                    up = false;
                }

                groupServerStatus.setServer(serverUp);
                groupServerStatus.setMember(memberUp);
                groupServerStatus.setPull(pullIn);
                groupServerStatus.setHealthy(raise);
                groupServerStatus.setUp(up);
                status.addGroupServerStatus(groupServerStatus);
            }
            res.add(status);
        }
        return res;
    }
}
