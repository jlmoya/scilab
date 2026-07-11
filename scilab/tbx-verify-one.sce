// Runs inside a throwaway scilab-adv-cli (see tbx-verify-all.sh):
// verify $TBX_NAME, append one TSV line to $TBX_OUT, exit 0 on pass / 1 on fail.
name = getenv("TBX_NAME");
out  = getenv("TBX_OUT");
r = tbxVerify(name);
status = "FAIL"; detail = r.err;
if r.pass then
    status = "PASS";
    detail = "delta=" + string(r.delta) + "; smoke=" + r.smoke;
end
fd = mopen(out, "w");
mfprintf(fd, "%s\t%s\t%s\n", name, status, detail);
mclose(fd);
exit(1 - bool2s(r.pass));
