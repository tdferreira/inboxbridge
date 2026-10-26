#!/usr/bin/env python3
"""Regression coverage for release naming, context isolation and publication gates."""
import importlib.util
from pathlib import Path
import tempfile
import sys
import json
import os
import shutil
import subprocess
import uuid
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec=importlib.util.spec_from_file_location(name, Path(__file__).with_name(name+'.py'))
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ContainerReleaseTests(unittest.TestCase):
    def test_official_and_fork_names_share_the_same_release_contract(self):
        helper=load('image-metadata')
        for repo in ('tdferreira/inboxbridge','ExampleOwner/inboxbridge'):
            result=helper.metadata(repo,'v1.2.3-rc.1','a'*40)
            self.assertEqual(result['backend_image'],'ghcr.io/'+repo.lower()+'-backend')
            self.assertEqual(result['frontend_image'],'ghcr.io/'+repo.lower()+'-frontend')
            self.assertEqual(result['image_tag'],'v1.2.3-rc.1')
        for bad in ('../main','v1.2.3$(touch injected)','v1.2.3\nextra=true','v1.2.3+build'):
            with self.assertRaises(ValueError): helper.metadata('example/repo',bad,'a'*40)
        with self.assertRaises(ValueError): helper.metadata('example/repo','v1.2.3','main')

    def test_context_contains_only_built_outputs_and_public_runtime_config(self):
        helper=load('prepare-images')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)/'source'; out=Path(directory)/'context'
            for name,body in {'target/quarkus-app/quarkus-run.jar':'runtime',
                              'admin-ui/dist/index.html':'<html>', 'admin-ui/nginx.conf':'nginx',
                              'admin-ui/nginx-main.conf':'nginx', 'LICENSE':'license',
                              'docker/release/ingress/default.conf.template':'proxy template',
                              'docker/release/ingress/entrypoint.sh':'proxy entrypoint',
                              '.env':'synthetic-private-canary', 'certs/ca.key':'synthetic-private-canary',
                              '.dev-local/encryption-key':'synthetic-private-canary'}.items():
                file=root/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(body)
            with patch.object(helper,'ROOT',root): helper.prepare(out)
            self.assertEqual({str(p.relative_to(out)) for p in out.rglob('*') if p.is_file()},
                             {'backend/quarkus-run.jar','frontend/index.html','nginx.conf','nginx-main.conf','LICENSE',
                              'ingress/default.conf.template','ingress/entrypoint.sh'})
            with patch.object(helper,'ROOT',root), self.assertRaises(ValueError): helper.prepare(out)

    def test_missing_outputs_fail_before_context_creation(self):
        helper=load('prepare-images')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);out=root/'context'
            with patch.object(helper,'ROOT',root), self.assertRaises(ValueError): helper.prepare(out)
            self.assertFalse(out.exists())

    def test_image_override_preserves_ingress_and_isolation_without_reading_operator_env(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary=Path(directory)
            shutil.copyfile(ROOT/'docker-compose.yml',temporary/'base.yml')
            (temporary/'.env').write_text('')
            env={**os.environ,'INBOXBRIDGE_BACKEND_IMAGE':'example/backend:v1.2.3',
                 'INBOXBRIDGE_FRONTEND_IMAGE':'example/frontend:v1.2.3',
                 'PUBLIC_BIND_ADDRESS':'127.0.0.1','PUBLIC_PORT':'3000'}
            result=subprocess.check_output(['docker','compose','--project-directory',directory,
                '--env-file',str(temporary/'.env'),'-f',str(temporary/'base.yml'),
                '-f',str(ROOT/'docker-compose.images.yml'),'config','--format','json'],env=env,text=True)
            services=json.loads(result)['services']
            self.assertEqual(services['inboxbridge']['image'],'example/backend:v1.2.3')
            self.assertEqual(services['inboxbridge-admin']['image'],'example/frontend:v1.2.3')
            self.assertFalse(services['inboxbridge'].get('ports'))
            self.assertFalse(services['postgres'].get('ports'))
            self.assertEqual(services['inboxbridge-admin']['ports'][0]['host_ip'],'127.0.0.1')

    def test_disposable_root_owned_certificates_can_be_removed_without_capabilities(self):
        volume='inboxbridge-cleanup-test-'+uuid.uuid4().hex
        subprocess.run(['docker','volume','create',volume],check=True,capture_output=True)
        mount=['--mount',f'type=volume,source={volume},target=/certs']
        try:
            subprocess.run(['docker','run','--rm','--network','none',*mount,'alpine:3.24',
                'sh','-c','mkdir /certs/private; chmod 700 /certs/private; echo synthetic > /certs/private/key'],check=True,capture_output=True)
            denied=subprocess.run(['docker','run','--rm','--network','none','--user','1001:1001',
                *mount,'alpine:3.24','sh','-c','rm -rf /certs/private'],capture_output=True)
            self.assertNotEqual(denied.returncode,0)
            subprocess.run(['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL',
                '--security-opt','no-new-privileges',*mount,'alpine:3.24','sh','-c','rm -rf /certs/*'],check=True,capture_output=True)
            files=subprocess.check_output(['docker','run','--rm','--network','none',*mount,
                'alpine:3.24','ls','-A','/certs'],text=True)
            self.assertEqual(files,'')
        finally:
            subprocess.run(['docker','volume','rm',volume],check=True,capture_output=True)

    def test_same_images_preserve_command_override_and_explicit_tls_choice(self):
        backend=(ROOT/'docker/release/Dockerfile.backend').read_text()
        self.assertIn('CMD ["java"',backend)
        self.assertNotIn('ENTRYPOINT',backend)
        standard=(ROOT/'docker/release/Dockerfile.frontend').read_text()
        self.assertIn('nginx.conf /etc/nginx/inboxbridge/internal-tls.conf',standard)
        self.assertIn('ENV TLS_ENABLED=true',standard)
        self.assertIn('listen 8443 ssl',(ROOT/'admin-ui/nginx.conf').read_text())
        proxy=(ROOT/'docker/release/ingress/default.conf.template').read_text()
        self.assertIn('server inboxbridge:8080 resolve',proxy)
        self.assertIn('proxy_set_header X-Forwarded-Proto https;',proxy)
        self.assertIn('proxy_set_header X-Forwarded-Port 443;',proxy)
        self.assertIn('proxy_set_header Forwarded "";',proxy)
        self.assertNotIn('proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for',proxy)
        for workflow in ('build','release'):
            self.assertIn('sh scripts/release/smoke-proxy-images.sh',
                          (ROOT/'.github/workflows'/f'{workflow}.yml').read_text())
        entrypoint=(ROOT/'docker/release/ingress/entrypoint.sh').read_text()
        self.assertIn('case "${TLS_ENABLED-true}"',entrypoint)
        self.assertIn('TLS_ENABLED=true requires readable',entrypoint)

    def test_release_checks_exact_tag_and_tests_before_registry_publication(self):
        workflow=(ROOT/'.github/workflows/release.yml').read_text()
        self.assertIn('ref: ${{ steps.release_meta.outputs.tag_name }}',workflow)
        gates=['Verify backend and package runtime','Test frontend',
               'Rebuild production PostgreSQL runtime','Build and smoke-test release images',
               'Publish versioned multiarchitecture images','Publish GitHub release']
        positions=[workflow.index('- name: '+gate) for gate in gates]
        self.assertEqual(positions,sorted(positions))
        self.assertIn('packages: write',workflow)
        publish=(ROOT/'scripts/release/publish-images.sh').read_text()
        self.assertIn('--platform linux/amd64,linux/arm64',publish)
        self.assertNotIn(':latest',publish)
        self.assertIn('--provenance=true --sbom=true',publish)
        smoke=(ROOT/'scripts/release/smoke-images.sh').read_text()
        self.assertIn('compose cp cert-init:/certs/ca.crt',smoke)
        self.assertNotIn('--insecure',smoke)
        self.assertIn('https://localhost:',smoke)
        self.assertIn('--network none --read-only --cap-drop ALL',smoke)
        self.assertIn('source=$task_tmp/certs,target=/certs',smoke)
        self.assertIn('sh scripts/release/smoke-images.sh',(ROOT/'.github/workflows/build.yml').read_text())


if __name__=='__main__': unittest.main()
