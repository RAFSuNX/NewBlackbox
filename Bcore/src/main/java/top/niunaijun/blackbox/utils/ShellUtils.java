package top.niunaijun.blackbox.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class ShellUtils {
    // su is defined but never used with isRoot=true in this codebase —
    // the sandbox runs as a normal user process, root is not required.
    public static final String COMMAND_SU = "su";
    public static final String COMMAND_SH = "sh";

    private ShellUtils() {
        throw new AssertionError();
    }

    public static boolean checkRootPermission() {
        return execCommand("echo root", true).result == 0;
    }

    public static CommandResult execCommand(String command, boolean isRoot) {
        return execCommand(new String[]{command}, isRoot, true);
    }

    public static CommandResult execCommand(String[] commands, boolean isRoot, boolean isNeedResultMsg) {
        if (commands == null || commands.length == 0) return new CommandResult(-1, null);

        Process process = null;
        StringBuilder successMsg = null;
        try {
            process = Runtime.getRuntime().exec(isRoot ? COMMAND_SU : COMMAND_SH);
            try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
                for (String command : commands) {
                    if (command == null) continue;
                    os.write(command.getBytes());
                    os.write('\n');
                    os.flush();
                }
                os.writeBytes("exit\n");
                os.flush();
            }
            int result = process.waitFor();
            if (isNeedResultMsg) {
                successMsg = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) successMsg.append(line).append('\n');
                }
            }
            return new CommandResult(result, successMsg == null ? null : successMsg.toString());
        } catch (IOException | InterruptedException e) {
            return new CommandResult(-1, null);
        } finally {
            if (process != null) process.destroy();
        }
    }

    public static class CommandResult {
        public final int result;
        public final String successMsg;

        public CommandResult(int result) {
            this(result, null);
        }

        public CommandResult(int result, String successMsg) {
            this.result = result;
            this.successMsg = successMsg;
        }
    }
}
