#!/usr/bin/env bash
# One Scilab per toolbox: a loader that calls exit() (scimax does) must not be
# able to end the sweep, and -nouserstartup avoids autoloading every toolbox
# first and then verifying an already-loaded one.
SCILAB=/Applications/Scilab-2027.0.0.app/Contents/MacOS/Scilab-2027.0.0
name="$1"
cat > "/tmp/verify_$name.sce" <<EOF
mode(-1);
ie = execstr("R = tbxVerify(""$name"");", "errcatch");
if ie <> 0 then
    mprintf("\nRESULT|$name|RAISED|%s\n", part(lasterror()(1),1:70));
else
    mprintf("\nRESULT|$name|%s|%s|%s\n", string(bool2s(R.pass)), R.smoke, R.err);
end
exit(0);
EOF
out=$(timeout 180 "$SCILAB" -nwni -nb -nouserstartup -quit -f "/tmp/verify_$name.sce" </dev/null 2>&1 | grep "^RESULT|")
rc=$?
if [ -z "$out" ]; then echo "RESULT|$name|DIED|process ended without a result (rc=$rc)"; else echo "$out"; fi
rm -f "/tmp/verify_$name.sce"
