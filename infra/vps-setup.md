# VPS setup (AIC Cloud, 1 GB RAM)

One-time setup, as root:

```bash
# 2 GB swap as a safety net
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl vm.swappiness=10 && echo 'vm.swappiness=10' >> /etc/sysctl.conf

# Docker Engine + compose plugin
curl -fsSL https://get.docker.com | sh

# Deploy user used by GitHub Actions
useradd -m -s /bin/bash -G docker deploy
mkdir -p /opt/vaabhi && chown deploy:deploy /opt/vaabhi
# add the public half of VPS_SSH_KEY to /home/deploy/.ssh/authorized_keys
```

Then, as `deploy`:

```bash
cp .env.example /opt/vaabhi/.env && chmod 600 /opt/vaabhi/.env   # fill in values
echo "<github-token-with-read:packages>" | docker login ghcr.io -u <github-user> --password-stdin
```

Memory budget: OS + Docker ~210 MB, cloudflared ~30 MB, backend ~320 MB, storefront ~150 MB.
