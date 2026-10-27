package com.aromatica.modules.finance.money.model;

import java.util.UUID;

public interface AccountBalanceRow {
    UUID getAccountId();
    long getBalanceMinor();
}
