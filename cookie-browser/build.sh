set -e
AJAR=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf out && mkdir -p out/classes out/apk
# 1. aapt: compile resources + generate R.java
aapt package -f -m -J out -M AndroidManifest.xml -S res -I $AJAR -F out/apk/res.ap_ --min-sdk-version 21 --target-sdk-version 34
# 2. javac
find src out -name "*.java" > out/sources.txt
javac -source 8 -target 8 -bootclasspath $AJAR -classpath $AJAR -d out/classes @out/sources.txt
# 3. dx -> dex
java -jar /usr/lib/android-sdk/build-tools/debian/lib/dx.jar --dex --output=out/apk/classes.dex out/classes
# 4. package apk (res.ap_ already has resources + manifest)
cd out/apk
cp res.ap_ app.unsigned.apk
aapt add app.unsigned.apk classes.dex
cd ../..
# 5. zipalign
zipalign -f 4 out/apk/app.unsigned.apk out/apk/app.aligned.apk
echo BUILD_OK
