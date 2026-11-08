package repro.web;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.util.concurrent.Callable;

/** Runs a call in a new transaction that is always rolled back, so that the cases do not influence each other. */
@ApplicationScoped
public class Runner {
    @Resource
    TransactionSynchronizationRegistry registry;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String committed(Callable<String> call) throws Exception {
        return call.call();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public String withoutTransaction(Callable<String> call) throws Exception {
        return call.call();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String inRolledBackTransaction(Callable<String> call) throws Exception {
        try {
            return call.call();
        } finally {
            registry.setRollbackOnly();
        }
    }
}
