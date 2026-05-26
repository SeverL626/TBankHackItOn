#!/usr/bin/env bash
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-fastuser}"
APP_DIR="${APP_DIR:-/opt/myapp}"

apt-get update
apt-get install -y ca-certificates curl gnupg ufw

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker "$DEPLOY_USER"

mkdir -p "$APP_DIR"
chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

install -d -m 700 "/home/$DEPLOY_USER/.ssh"
chown "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"

if [ ! -f "/home/$DEPLOY_USER/.ssh/github_actions_deploy" ]; then
  runuser -u "$DEPLOY_USER" -- ssh-keygen -t ed25519 -N "" -f "/home/$DEPLOY_USER/.ssh/github_actions_deploy"
fi

cat "/home/$DEPLOY_USER/.ssh/github_actions_deploy.pub" >> "/home/$DEPLOY_USER/.ssh/authorized_keys"
sort -u "/home/$DEPLOY_USER/.ssh/authorized_keys" -o "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh/authorized_keys"

echo
echo "Server bootstrap finished."
echo "Use this private key as GitHub secret VPS_SSH_KEY:"
cat "/home/$DEPLOY_USER/.ssh/github_actions_deploy"
