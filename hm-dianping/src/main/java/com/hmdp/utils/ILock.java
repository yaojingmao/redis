package com.hmdp.utils;

public interface ILock {

    public boolean tryLock(long timeOutSec);

    void unLock();


}
