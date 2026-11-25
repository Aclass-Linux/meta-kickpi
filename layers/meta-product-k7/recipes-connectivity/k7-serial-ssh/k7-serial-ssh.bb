SUMMARY = "Provision root SSH access from the K7 serial console"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^kickpi-k7$"
PACKAGE_ARCH = "${MACHINE_ARCH}"
SRC_URI = "file://k7-first-serial-login.sh file://k7-enable-root-ssh"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = "openssh-sshd openssh-keygen util-linux-flock"

do_install() {
    install -d ${D}${sysconfdir}/profile.d ${D}${sbindir}
    sed -e 's|@K7_CONSOLE_DEVICE@|${K7_CONSOLE_DEVICE}|g' \
        -e 's|@SBINDIR@|${sbindir}|g' \
        ${S}/k7-first-serial-login.sh > ${D}${sysconfdir}/profile.d/k7-first-serial-login.sh
    chmod 0644 ${D}${sysconfdir}/profile.d/k7-first-serial-login.sh
    install -m 0755 ${S}/k7-enable-root-ssh ${D}${sbindir}/k7-enable-root-ssh
}
