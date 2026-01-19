/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2016 - 2016 - Scilab Enterprises - Clement DAVID
 * Copyright (C) 2025 - Dassault Systèmes S.E. - Clement DAVID
 *
 * Copyright (C) 2012 - 2016 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

#include <windows.h>
#include <TlHelp32.h>
#include <dbghelp.h>
#pragma comment(lib, "Dbghelp.lib")

#include <vector>
#include <iostream>

extern "C"
{
#include "SignalManagement.h"
}

// simple helper to read memory for StackWalk64 (uses s_dbgTargetProcess)
static HANDLE s_dbgTargetProcess = nullptr;
static BOOL CALLBACK ReadRemoteMemory(HANDLE /*hProcess*/, DWORD64 qwBaseAddress, PVOID lpBuffer, DWORD nSize, LPDWORD lpNumberOfBytesRead)
{
    SIZE_T bytesRead = 0;
    BOOL ok = ReadProcessMemory(s_dbgTargetProcess, (LPCVOID)(uintptr_t)qwBaseAddress, lpBuffer, (SIZE_T)nSize, &bytesRead);
    if (lpNumberOfBytesRead)
    {
        *lpNumberOfBytesRead = (DWORD64)bytesRead;
    }
    return ok;
};

static VOID CALLBACK kill_process_callback(PTP_CALLBACK_INSTANCE Instance, PVOID Context, PTP_TIMER Timer)
{
    std::cerr << "Watchdog timer expired: Scilab killed" << std::endl;

    // Implement stack trace reporting here!
    // Best-effort: enumerate current process threads and child processes' threads,
    // suspend them, capture CONTEXT and walk stacks using StackWalk64 + DbgHelp.
    // Note: simplified (x64-only, ignores WoW64/32-bit processes). Non-fatal on failures.

    auto log_stack_for_thread = [&](HANDLE hProcess, HANDLE hThread, DWORD tid)
    {
        CONTEXT ctx;
        ZeroMemory(&ctx, sizeof(ctx));
#ifdef _M_X64
        ctx.ContextFlags = CONTEXT_FULL;
#else
        ctx.ContextFlags = CONTEXT_FULL;
#endif
        bool suspended = false;
        // Try to suspend thread to get a consistent context
        if (SuspendThread(hThread) != (DWORD)-1)
        {
            suspended = true;
        }

        if (!GetThreadContext(hThread, &ctx))
        {
            if (suspended) ResumeThread(hThread);
            return;
        }

        // Initialize stack frame
        STACKFRAME64 frame;
        ZeroMemory(&frame, sizeof(frame));
#ifdef _M_X64
        DWORD machine = IMAGE_FILE_MACHINE_AMD64;
        frame.AddrPC.Offset = ctx.Rip;
        frame.AddrFrame.Offset = ctx.Rbp;
        frame.AddrStack.Offset = ctx.Rsp;
#else
        DWORD machine = IMAGE_FILE_MACHINE_I386;
        frame.AddrPC.Offset = ctx.Eip;
        frame.AddrFrame.Offset = ctx.Ebp;
        frame.AddrStack.Offset = ctx.Esp;
#endif
        frame.AddrPC.Mode = frame.AddrFrame.Mode = frame.AddrStack.Mode = AddrModeFlat;

        // Prepare symbol buffer
        BYTE symBuffer[sizeof(SYMBOL_INFO) + MAX_SYM_NAME * sizeof(TCHAR)];
        PSYMBOL_INFO pSymbol = reinterpret_cast<PSYMBOL_INFO>(symBuffer);
        pSymbol->SizeOfStruct = sizeof(SYMBOL_INFO);
        pSymbol->MaxNameLen = MAX_SYM_NAME;

        std::cerr << "Thread " << tid << " stack:" << std::endl;

        // Set global process used by the ReadRemoteMemory callback
        s_dbgTargetProcess = hProcess;

        // Walk
        for (int frameNum = 0; frameNum < 64; ++frameNum)
        {
            BOOL ok = StackWalk64(
                machine,
                hProcess,
                hThread,
                &frame,
                &ctx,
                ReadRemoteMemory,
                SymFunctionTableAccess64,
                SymGetModuleBase64,
                nullptr);

            if (!ok || frame.AddrPC.Offset == 0)
            {
                break;
            }

            DWORD64 address = frame.AddrPC.Offset;
            DWORD64 displacement = 0;
            if (SymFromAddr(hProcess, address, &displacement, pSymbol))
            {
                std::cerr << "  #" << frameNum << " " << pSymbol->Name << " + 0x" << std::hex << displacement << std::dec << " [0x" << std::hex << address << std::dec << "]" << std::endl;
            }
            else
            {
                std::cerr << "  #" << frameNum << " [0x" << std::hex << address << std::dec << "]" << std::endl;
            }
        }

        s_dbgTargetProcess = nullptr;

        if (suspended) ResumeThread(hThread);
    };

    auto log_process_threads = [&](DWORD pid)
    {
        HANDLE hProcess = nullptr;
        bool opened = false;
        if (pid == GetCurrentProcessId())
        {
            hProcess = GetCurrentProcess();
        }
        else
        {
            hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
            opened = true;
        }

        if (!hProcess)
        {
            return;
        }

        // Initialize symbols for the target process
        SymSetOptions(SYMOPT_DEFERRED_LOADS | SYMOPT_UNDNAME);
        if (!SymInitialize(hProcess, nullptr, TRUE))
        {
            // continue anyway; Sym* may fail but we'll still try StackWalk64 + addresses
        }

        // enumerate threads
        HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
        if (hSnap != INVALID_HANDLE_VALUE)
        {
            THREADENTRY32 te;
            te.dwSize = sizeof(te);
            if (Thread32First(hSnap, &te))
            {
                do
                {
                    if (te.th32OwnerProcessID != pid) continue;

                    DWORD tid = te.th32ThreadID;
                    HANDLE hThread = OpenThread(THREAD_SUSPEND_RESUME | THREAD_GET_CONTEXT | THREAD_QUERY_INFORMATION, FALSE, tid);
                    if (!hThread)
                    {
                        // try with less access just to resume/suspend if possible
                        hThread = OpenThread(THREAD_QUERY_INFORMATION, FALSE, tid);
                    }
                    if (!hThread) continue;

                    log_stack_for_thread(hProcess, hThread, tid);

                    CloseHandle(hThread);
                } while (Thread32Next(hSnap, &te));
            }
            CloseHandle(hSnap);
        }

        SymCleanup(hProcess);
        if (opened) CloseHandle(hProcess);
    };

    // collect child processes + current process
    DWORD mypid = GetCurrentProcessId();
    std::vector<DWORD> pids;
    pids.push_back(mypid);

    HANDLE hProcSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hProcSnap != INVALID_HANDLE_VALUE)
    {
        PROCESSENTRY32 pe;
        pe.dwSize = sizeof(pe);
        if (Process32First(hProcSnap, &pe))
        {
            do
            {
                if (pe.th32ParentProcessID == mypid)
                {
                    pids.push_back(pe.th32ProcessID);
                }
            } while (Process32Next(hProcSnap, &pe));
        }
        CloseHandle(hProcSnap);
    }

    for (DWORD pid : pids)
    {
        std::cerr << "Process " << pid << " backtraces:" << std::endl;
        log_process_threads(pid);
    }

    // use the System Error code: wait operation timed out
    ExitProcess(258);
}

void timeout_process_after(int timeoutDelay)
{
    auto timerid = CreateThreadpoolTimer(kill_process_callback, nullptr, nullptr);

    FILETIME FileDueTime;
    ULARGE_INTEGER ulDueTime;

    // Set the timer to fire in the delay in seconds, relative to the current time
	long long in_seconds = - 10 * 1000 * 1000;
    ulDueTime.QuadPart = timeoutDelay * in_seconds;
    FileDueTime.dwHighDateTime = ulDueTime.HighPart;
    FileDueTime.dwLowDateTime  = ulDueTime.LowPart;

    SetThreadpoolTimer(timerid, &FileDueTime, 0, 0);
}
