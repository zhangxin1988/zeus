package com.ctrip.zeus.service.model;

import com.ctrip.zeus.model.entity.VirtualServer;

import java.util.List;

/**
 * Created by zhoumy on 2015/7/27.
 */
public interface VirtualServerRepository {

    List<VirtualServer> listAll(Long[] vsIds) throws Exception;

    List<VirtualServer> listAll(IdVersion[] keys) throws Exception;

    VirtualServer getById(Long vsId) throws Exception;

    VirtualServer getById(Long vsId, ModelMode mode) throws Exception;

    VirtualServer add(Long slbId, VirtualServer virtualServer) throws Exception;

    void update(VirtualServer virtualServer) throws Exception;

    void delete(Long virtualServerId) throws Exception;

    void installCertificate(VirtualServer virtualServer) throws Exception;
}
