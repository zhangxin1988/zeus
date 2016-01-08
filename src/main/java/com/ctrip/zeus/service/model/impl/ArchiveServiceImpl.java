package com.ctrip.zeus.service.model.impl;

import com.ctrip.zeus.dal.core.*;
import com.ctrip.zeus.model.entity.*;
import com.ctrip.zeus.model.transform.DefaultSaxParser;
import com.ctrip.zeus.service.model.ArchiveService;

import com.ctrip.zeus.service.model.IdVersion;
import com.ctrip.zeus.service.model.ModelMode;
import com.ctrip.zeus.support.C;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author:xingchaowang
 * @date: 3/14/2015.
 */
@Component("archiveService")
public class ArchiveServiceImpl implements ArchiveService {
    @Resource
    private ArchiveSlbDao archiveSlbDao;
    @Resource
    private ArchiveGroupDao archiveGroupDao;
    @Resource
    private ArchiveVsDao archiveVsDao;
    @Resource
    private RGroupStatusDao rGroupStatusDao;

    @Override
    public int archiveSlb(Slb slb) throws Exception {
        String content = String.format(Slb.XML, slb);
        ArchiveSlbDo d = new ArchiveSlbDo().setSlbId(slb.getId()).setContent(content).setVersion(slb.getVersion()).setCreatedTime(new Date()).setDataChangeLastTime(new Date());
        archiveSlbDao.insert(d);
        return d.getVersion();
    }

    @Override
    public int archiveGroup(Group group) throws Exception {
        String content = String.format(Group.XML, group);
        ArchiveGroupDo d = new ArchiveGroupDo().setGroupId(group.getId()).setContent(content).setVersion(group.getVersion()).setCreatedTime(new Date()).setDataChangeLastTime(new Date());
        archiveGroupDao.insert(d);
        return d.getVersion();
    }

    @Override
    public int deleteSlbArchive(Long slbId) throws Exception {
        ArchiveSlbDo d = new ArchiveSlbDo().setSlbId(slbId);
        return archiveSlbDao.deleteBySlb(d);
    }

    @Override
    public int deleteGroupArchive(Long groupId) throws Exception {
        ArchiveGroupDo d = new ArchiveGroupDo().setGroupId(groupId);
        return archiveGroupDao.deleteByGroup(d);
    }

    @Override
    public Slb getSlb(Long slbId, int version) throws Exception {
        ArchiveSlbDo d = archiveSlbDao.findBySlbAndVersion(slbId, version, ArchiveSlbEntity.READSET_FULL);
        return d == null ? null : DefaultSaxParser.parseEntity(Slb.class, d.getContent());
    }

    @Override
    public Group getGroup(Long groupId, int version) throws Exception {
        ArchiveGroupDo d = archiveGroupDao.findByGroupAndVersion(groupId, version, ArchiveGroupEntity.READSET_FULL);
        return d == null ? null : DefaultSaxParser.parseEntity(Group.class, d.getContent());
    }

    @Override
    public Slb getLatestSlb(Long slbId) throws Exception {
        ArchiveSlbDo d = archiveSlbDao.findMaxVersionBySlb(slbId, ArchiveSlbEntity.READSET_FULL);
        return d == null ? null : DefaultSaxParser.parseEntity(Slb.class, d.getContent());
    }

    @Override
    public Group getGroupByMode(Long groupId, ModelMode mode) throws Exception {
        RelGroupStatusDo check = rGroupStatusDao.findByGroup(groupId, RGroupStatusEntity.READSET_FULL);
        if (check == null) return null;
        ArchiveGroupDo d;
        switch (mode) {
            case MODEL_MODE_ONLINE: {
                d = archiveGroupDao.findByGroupAndVersion(groupId, check.getOnlineVersion(), ArchiveGroupEntity.READSET_FULL);
                break;
            }
            case MODEL_MODE_OFFLINE:
            default: {
                d = archiveGroupDao.findByGroupAndVersion(groupId, check.getOfflineVersion(), ArchiveGroupEntity.READSET_FULL);
                break;
            }
        }
        return d == null ? null : DefaultSaxParser.parseEntity(Group.class, d.getContent());
    }

    @Override
    public VirtualServer getVirtualServerByMode(Long vsId, ModelMode mode) throws Exception {
        return null;
    }

    @Override
    public Slb getSlbByMode(Long slbId, ModelMode mode) throws Exception {
        return null;
    }

    @Override
    public List<Slb> getLatestSlbs(Long[] slbIds) throws Exception {
        List<Slb> slbs = new ArrayList<>();
        for (ArchiveSlbDo archiveSlbDo : archiveSlbDao.findMaxVersionBySlbs(slbIds, ArchiveSlbEntity.READSET_FULL)) {
            try {
                Slb slb = DefaultSaxParser.parseEntity(Slb.class, archiveSlbDo.getContent());
                slbs.add(slb);
            } catch (Exception ex) {
                slbs.add(new Slb().setId(archiveSlbDo.getId()));
            }
        }
        return slbs;
    }

    @Override
    public List<Group> getGroupsByMode(Long[] groupIds, ModelMode mode) throws Exception {
        List<Group> groups = new ArrayList<>();
        List<ArchiveGroupDo> dos;
        switch (mode) {
            case MODEL_MODE_OFFLINE: {
                dos = archiveGroupDao.findAllOfflineByGroups(groupIds, ArchiveGroupEntity.READSET_FULL);
                break;
            }
            case MODEL_MODE_ONLINE: {
                dos = archiveGroupDao.findAllOnlineByGroups(groupIds, ArchiveGroupEntity.READSET_FULL);
                break;
            }
            case MODEL_MODE_REDUNDANT: {
                dos = new ArrayList<>();
                dos.addAll(archiveGroupDao.findAllOfflineByGroups(groupIds, ArchiveGroupEntity.READSET_FULL));
                dos.addAll(archiveGroupDao.findAllOnlineByGroups(groupIds, ArchiveGroupEntity.READSET_FULL));
            }
            case MODEL_MODE_MERGE:
            default: {
                dos = archiveGroupDao.findAllByGroups(groupIds, ArchiveGroupEntity.READSET_FULL);
            }
        }
        for (ArchiveGroupDo archiveGroupDo : dos) {
            try {
                Group group = DefaultSaxParser.parseEntity(Group.class, archiveGroupDo.getContent());
                groups.add(group);
            } catch (Exception ex) {
                groups.add(new Group().setId(archiveGroupDo.getId()));
            }
        }
        return groups;
    }

    @Override
    public List<Group> listGroups(IdVersion[] keys) throws Exception {
        List<Group> groups = new ArrayList<>();
        Integer[] hashes = new Integer[keys.length];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = keys[i].hashCode();
        }
        for (ArchiveGroupDo d : archiveGroupDao.findAllByIdVersion(hashes, keys, ArchiveGroupEntity.READSET_FULL)) {
            Group group = DefaultSaxParser.parseEntity(Group.class, d.getContent());
            groups.add(group);
        }
        return groups;
    }

    @Override
    public List<VirtualServer> getVirtualServersByMode(Long[] vsIds, ModelMode mode) throws Exception {
        return null;
    }

    @Override
    public List<VirtualServer> listVirtualServers(IdVersion[] keys) throws Exception {
        return null;
    }

    @Override
    public List<Slb> getSlbsByMode(Long[] slbIds, ModelMode mode) throws Exception {
        return null;
    }

    @Override
    public Archive getLatestSlbArchive(Long slbId) throws Exception {
        ArchiveSlbDo asd = archiveSlbDao.findMaxVersionBySlb(slbId, ArchiveSlbEntity.READSET_FULL);
        return C.toSlbArchive(asd);
    }

    @Override
    public Archive getLatestGroupArchive(Long groupId) throws Exception {
        RelGroupStatusDo check = rGroupStatusDao.findByGroup(groupId, RGroupStatusEntity.READSET_FULL);
        if (check == null) return null;
        ArchiveGroupDo d = archiveGroupDao.findByGroupAndVersion(groupId, check.getOfflineVersion(), ArchiveGroupEntity.READSET_FULL);
        return C.toGroupArchive(d);
    }

    @Override
    public List<Archive> getLastestGroupArchives(Long[] groupIds) throws Exception {
        List<Archive> result = new ArrayList<>();
        for (ArchiveGroupDo archiveGroupDo : archiveGroupDao.findAllByGroups(groupIds, ArchiveGroupEntity.READSET_FULL)) {
            result.add(C.toGroupArchive(archiveGroupDo));
        }
        return result;
    }

    @Override
    public List<Archive> getLastestVsArchives(Long[] vsIds) throws Exception {
        List<Archive> result = new ArrayList<>();
        for (MetaVsArchiveDo metaVsArchiveDo : archiveVsDao.findMaxVersionByVses(vsIds, ArchiveVsEntity.READSET_FULL)) {
            result.add(C.toVsArchive(metaVsArchiveDo));
        }
        return result;
    }

    @Override
    public Archive getSlbArchive(Long slbId, int version) throws Exception {
        ArchiveSlbDo archive = archiveSlbDao.findBySlbAndVersion(slbId, version, ArchiveSlbEntity.READSET_FULL);
        return C.toSlbArchive(archive);
    }

    @Override
    public Archive getGroupArchive(Long groupId, int version) throws Exception {
        ArchiveGroupDo archive = archiveGroupDao.findByGroupAndVersion(groupId, version, ArchiveGroupEntity.READSET_FULL);
        return C.toGroupArchive(archive);
    }

    @Override
    public Archive getVsArchive(Long vsId, int version) throws Exception {
        MetaVsArchiveDo d = archiveVsDao.findByVsAndVersion(vsId, version, ArchiveVsEntity.READSET_FULL);
        return C.toVsArchive(d);
    }

    @Override
    public Archive getLatestVsArchive(Long vsId) throws Exception {
        MetaVsArchiveDo d = archiveVsDao.findMaxVersionByVs(vsId, ArchiveVsEntity.READSET_FULL);
        return C.toVsArchive(d);
    }

    @Override
    public List<Archive> getVsArchives(Long[] vsIds, Integer[] versions) throws Exception {
        String[] pairs = new String[vsIds.length];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = vsIds[i] + "," + versions[i];
        }
        List<MetaVsArchiveDo> list = archiveVsDao.findAllByVsAndVersion(vsIds, pairs, ArchiveVsEntity.READSET_IDONLY);
        Long[] ids = new Long[list.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = list.get(i).getId();
        }
        list = archiveVsDao.findAllByIds(ids, ArchiveVsEntity.READSET_FULL);
        List<Archive> result = new ArrayList<>(list.size());
        for (MetaVsArchiveDo d : list) {
            result.add(new Archive().setId(d.getVsId()).setContent(d.getContent()).setVersion(d.getVersion()));
        }
        return result;
    }

    @Override
    public List<Archive> getGroupArchives(Long[] groupIds, Integer[] versions) throws Exception {
        String[] pairs = new String[groupIds.length];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = groupIds[i] + "," + versions[i];
        }
        List<ArchiveGroupDo> list = archiveGroupDao.findAllByGroupAndVersion(groupIds, pairs, ArchiveGroupEntity.READSET_IDONLY);
        Long[] ids = new Long[list.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = list.get(i).getId();
        }
        list = archiveGroupDao.findAllByIds(ids, ArchiveGroupEntity.READSET_FULL);
        List<Archive> result = new ArrayList<>(list.size());
        for (ArchiveGroupDo d : list) {
            result.add(C.toGroupArchive(d));
        }
        return result;
    }
}