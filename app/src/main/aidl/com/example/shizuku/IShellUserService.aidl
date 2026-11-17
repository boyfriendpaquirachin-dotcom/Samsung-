package com.example.shizuku;

import android.os.Bundle;

interface IShellUserService {
    void destroy() = 16777114;
    Bundle executeCommand(String command) = 1;
    int getUid() = 2;
}
