package quan.data;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quan.data.field.Field;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Boolean.FALSE;

/**
 * 事务实现，多线程并发时需要自己加锁，否则隔离级别就是【读已提交】
 */
public class Transaction {

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);

    /**
     * 在事务外是否能修改数据，全局范围生效
     */
    private static boolean globalOptional = false;

    /**
     * 在事务外是否能修改数据，仅对线程范围生效
     */
    private static final ThreadLocal<Boolean> localOptional = ThreadLocal.withInitial(FALSE::booleanValue);

    /**
     * 保存事务为线程本地变量
     */
    private static final ThreadLocal<Transaction> threadLocal = new ThreadLocal<>();

    /**
     * 事务是否已失败
     */
    boolean failed;

    /**
     * 数据日志
     */
    private Map<Data<?>, Data.Log> dataLogs = new LinkedHashMap<>();

    /**
     * 节点日志
     */
    private Map<Node, Node.Log> nodeLogs = new HashMap<>();

    /**
     * 字段日志
     */
    private Map<Field, Object> fieldLogs = new HashMap<>();

    /**
     * 在事务执行结束之后再执行的特殊任务
     */
    private List<Listener> listeners = new ArrayList<>();

    /**
     * @see Savepoint
     */
    @SuppressWarnings("SpellCheckingInspection")
    private final Savepoint[] savepoints = new Savepoint[8];

    /**
     * 事务深度，开启一个内嵌事务加一
     */
    private int depth = 1;

    /**
     * @see #globalOptional
     */
    public static void setGlobalOptional(boolean globalOptional) {
        Transaction.globalOptional = globalOptional;
    }

    /**
     * @see #localOptional
     */
    public static void setLocalOptional(boolean localOptional) {
        Transaction.localOptional.set(localOptional);
    }

    public static boolean isOptional() {
        return globalOptional || localOptional.get();
    }

    void setDataLog(Data<?> data, DataAccessor accessor, Data.State state) {
        dataLogs.put(data, new Data.Log(accessor, state));
    }

    Data.Log getDataLog(Data<?> data) {
        Data.Log log = dataLogs.get(data);
        if (log != null) {
            return log;
        }

        for (int i = depth - 2; i >= 0; i--) {
            log = savepoints[i].dataLogs.get(data);
            if (log != null) {
                return log;
            }
        }

        return null;
    }

    Data.Log getPrevDataLog(Data<?> data, Data.Log log) {
        boolean finded = dataLogs.get(data) == log;

        for (int i = depth - 2; i >= 0; i--) {
            Data.Log _log = savepoints[i].dataLogs.get(data);
            if (finded && _log != null) {
                return _log;
            }
            if (_log == log) {
                finded = true;
            }
        }

        return null;
    }

    void setFieldLog(Field field, Object value, Data<?> owner, int position) {
        fieldLogs.put(field, value);

        if (field instanceof Node) {
            setNodeLog((Node) field, owner, position);
        }

        if (owner != null) {
            Data.Log dataLog = getDataLog(owner);
            if (dataLog == null && owner.state == Data.State.UPDATE) {
                setDataLog(owner, owner._getAccessor(), owner.state);
                dataLog = getDataLog(owner);
            }
            if (dataLog != null) {
                dataLog.updatedFields.set(position);
            }
        }
    }

    Object getFieldLog(Field field) {
        Object log = fieldLogs.get(field);
        if (log != null) {
            return log;
        }

        for (int i = depth - 2; i >= 0; i--) {
            log = savepoints[i].fieldLogs.get(field);
            if (log != null) {
                return log;
            }
        }

        return null;
    }

    void setNodeLog(Node node, Data<?> owner, int position) {
        Node.Log log = nodeLogs.computeIfAbsent(node, k -> new Node.Log());
        log.owner = owner;
        log.position = position;
    }

    Node.Log getNodeLog(Node node) {
        Node.Log log = nodeLogs.get(node);
        if (log != null) {
            return log;
        }

        for (int i = depth - 2; i >= 0; i--) {
            log = savepoints[i].nodeLogs.get(node);
            if (log != null) {
                return log;
            }
        }

        return null;
    }

    /**
     * 判断当前是否处于事务之中
     */
    public static boolean isInside() {
        return threadLocal.get() != null;
    }

    /**
     * 获取当前事务
     */
    public static Transaction get(boolean validate) {
        Transaction transaction = threadLocal.get();
        if (validate && transaction == null) {
            Validations.transactionError();
        }
        return transaction;
    }

    /**
     * 获取当前事务
     */
    public static Transaction get() {
        return threadLocal.get();
    }

    /**
     * 在事务中执行任务
     *
     * @param task   执行逻辑，带返回结果
     * @param nested 如果在事务中再次开启事务，是开启内嵌事务还是直接使用当前事务
     * @return 执行结果
     */
    public static <R> R run(Supplier<R> task, boolean nested) {
        Transaction transaction = Transaction.begin(nested);
        try {
            return task.get();
        } catch (Throwable e) {
            transaction.failed = true;
            throw e;
        } finally {
            end(transaction);
        }
    }

    /**
     * 在事务中执行任务
     *
     * @see #run(Supplier, boolean)
     */
    public static <R> R run(Supplier<R> task) {
        return run(task, false);
    }


    /**
     * 在事务中执行任务
     *
     * @see #run(Runnable, boolean)
     */
    public static void run(Runnable task) {
        run(task, false);
    }

    /**
     * 在事务中执行任务
     *
     * @param task   执行逻辑
     * @param nested 如果在事务中再次开启事务，是开启内嵌事务还是直接使用当前事务
     */
    public static void run(Runnable task, boolean nested) {
        Transaction transaction = Transaction.begin(nested);
        try {
            task.run();
        } catch (Throwable e) {
            transaction.failed = true;
            throw e;
        } finally {
            end(transaction);
        }
    }

    /**
     * 开始事务
     */
    static Transaction begin(boolean nested) {
        Transaction transaction = threadLocal.get();
        if (transaction == null) {
            transaction = new Transaction();
            threadLocal.set(transaction);
        } else if (nested) {
            save(transaction);
        } else {
            throw new IllegalStateException("当前已经在事务中了");
        }
        return transaction;
    }

    /**
     * 结束当前事务
     */
    static void end(Transaction transaction) {
        if (transaction.depth > 1) {
            restore(transaction);
            return;
        }

        //清空当前线程持有的事务对象
        threadLocal.set(null);

        //事务执行成功，提交事务
        if (!transaction.failed) {
            transaction.commit();
        }

        //执行事务结束后的特殊任务
        int when = transaction.failed ? Listener.WHEN_FAILED : Listener.WHEN_SUCCEEDED;
        for (Listener listener : transaction.listeners) {
            if ((listener.when & when) == when) {
                try {
                    listener.task.run();
                } catch (Exception e) {
                    logger.error("", e);
                }
            }
        }
    }

    /**
     * 保存外层事务，开启内嵌事务
     */
    private static void save(Transaction transaction) {
        if (transaction.depth > transaction.savepoints.length) {
            throw new IllegalStateException("事务嵌套层数(" + (transaction.depth + 1) + ")太深了");
        }

        Savepoint savepoint = new Savepoint();
        savepoint.failed = transaction.failed;
        savepoint.dataLogs = transaction.dataLogs;
        savepoint.nodeLogs = transaction.nodeLogs;
        savepoint.fieldLogs = transaction.fieldLogs;
        savepoint.listeners = transaction.listeners;

        transaction.savepoints[transaction.depth - 1] = savepoint;
        transaction.depth++;

        transaction.failed = false;
        transaction.dataLogs = new HashMap<>();
        transaction.nodeLogs = new HashMap<>();
        transaction.fieldLogs = new HashMap<>();
        transaction.listeners = new ArrayList<>();
    }

    /**
     * 结束内嵌事务，恢复外层事务
     */
    private static void restore(Transaction transaction) {
        Savepoint savepoint = transaction.savepoints[transaction.depth - 2];
        transaction.savepoints[transaction.depth - 2] = null;
        transaction.depth--;

        if (!transaction.failed) {
            savepoint.dataLogs.putAll(transaction.dataLogs);
            savepoint.nodeLogs.putAll(transaction.nodeLogs);
            savepoint.fieldLogs.putAll(transaction.fieldLogs);
        }

        for (Listener listener : transaction.listeners) {
            if (transaction.failed) {
                if (listener.when == Listener.WHEN_SUCCEEDED) {
                    continue;
                }
                if (listener.when == Listener.WHEN_FAILED) {
                    listener.when = Listener.WHEN_FINISHED;
                }
            }
            savepoint.listeners.add(listener);
        }

        transaction.dataLogs = savepoint.dataLogs;
        transaction.nodeLogs = savepoint.nodeLogs;
        transaction.fieldLogs = savepoint.fieldLogs;
        transaction.failed = savepoint.failed;
        transaction.listeners = savepoint.listeners;
    }

    /**
     * 回滚当前事务
     */
    public static void rollback() {
        //标记事务为失败状态
        Transaction transaction = get(true);
        transaction.failed = true;
    }

    /**
     * 提交事务
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void commit() {
        for (Node node : nodeLogs.keySet()) {
            node.commit(nodeLogs.get(node));
        }

        for (Field field : fieldLogs.keySet()) {
            field.commit(fieldLogs.get(field));
        }

        Map<DataAccessor, Triple> writes = new HashMap<>();
        Function<DataAccessor, Triple> newTriple = k -> Triple.of(new LinkedHashSet(), new LinkedHashMap<>(), new LinkedHashSet<>());

        for (Data<?> data : dataLogs.keySet()) {
            Data.Log log = dataLogs.get(data);
            data.commit(log);

            if (log.accessor != null && log.state != null) {
                Triple<Set, Map, Set> write = writes.computeIfAbsent(log.accessor, newTriple);
                switch (log.state) {
                    case INSERT:
                        write.getLeft().add(data);
                        break;
                    case UPDATE:
                        Map<String, Object> patch = data._getPatch();
                        if (patch != null) {
                            write.getMiddle().put(data, patch);
                        }
                        break;
                    case DELETE:
                        write.getRight().add(data);
                        break;
                }
            }

            data._updatedFields.clear();
        }

        for (DataAccessor accessor : writes.keySet()) {
            try {
                Triple<Set, Map, Set> write = writes.get(accessor);
                accessor.write(write.getLeft(), write.getRight(), write.getMiddle());
            } catch (Exception e) {
                logger.error("内存事务提交后写数据库出错", e);
            }
        }

    }

    /**
     * 在当前事务执行成功之后再执行特殊任务
     */
    public static void onSucceeded(Runnable task) {
        Transaction transaction = get(true);
        transaction.listeners.add(new Listener(task, Listener.WHEN_SUCCEEDED));
    }

    /**
     * 在当前事务执行失败之后再执行特殊任务
     */
    public static void onFailed(Runnable task) {
        Transaction transaction = get(true);
        transaction.listeners.add(new Listener(task, Listener.WHEN_FAILED));
    }

    /**
     * 在当前事务执行完成(不管成功或者失败)之后再执行特殊任务
     */
    public static void onFinished(Runnable task) {
        Transaction transaction = get(true);
        transaction.listeners.add(new Listener(task, Listener.WHEN_FINISHED));
    }


    /**
     * 保存点，存储外层事务日志，支持内嵌事务独立回滚
     */
    private static class Savepoint {

        boolean failed;

        Map<Data<?>, Data.Log> dataLogs = new LinkedHashMap<>();

        Map<Node, Node.Log> nodeLogs = new HashMap<>();

        Map<Field, Object> fieldLogs = new HashMap<>();

        List<Listener> listeners = new ArrayList<>();

    }

    /**
     * 事务完成之后需要执行的监听器
     */
    private static class Listener {

        Runnable task;

        int when;

        //事务成功后执行
        static final int WHEN_SUCCEEDED = 1;

        //事务失败后执行
        static final int WHEN_FAILED = 2;

        //事务成功或者失败后都要执行
        static final int WHEN_FINISHED = 3;

        public Listener(Runnable task, int when) {
            this.task = task;
            this.when = when;
        }

    }

}
