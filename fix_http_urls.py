#!/usr/bin/env python3
"""
WebGoat v3 Cloud Blocker Fix: Hardcoded HTTP URLs
This script fixes the easiest cloud blocker by replacing HTTP URLs with HTTPS.
"""

import os
import re
import glob

def fix_http_urls_in_file(file_path):
    """Fix HTTP URLs in a single file"""
    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        original_content = content
        
        # Fix ASPECT_LOGO HTTP URLs
        content = re.sub(
            r'setHref\("http://www\.aspectsecurity\.com"\)',
            'setHref("https://www.aspectsecurity.com")',
            content
        )
        
        # Fix comment HTTP URLs (less critical but good practice)
        content = re.sub(
            r'<a href="http://www\.aspectsecurity\.com">',
            '<a href="https://www.aspectsecurity.com">',
            content
        )
        
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            return True
        
        return False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

def main():
    """Main function to fix HTTP URLs in WebGoat Java files"""
    base_path = r"c:\Apps\WebGoat_v3\webgoat\JavaSource"
    
    if not os.path.exists(base_path):
        print(f"Error: Path {base_path} does not exist")
        return
    
    # Find all Java files
    java_files = glob.glob(os.path.join(base_path, "**", "*.java"), recursive=True)
    
    fixed_files = []
    total_files = len(java_files)
    
    print(f"Scanning {total_files} Java files for HTTP URLs...")
    
    for java_file in java_files:
        if fix_http_urls_in_file(java_file):
            fixed_files.append(java_file)
            print(f"Fixed: {os.path.relpath(java_file, base_path)}")
    
    print(f"\n✅ Cloud Blocker Fix Complete!")
    print(f"📊 Summary:")
    print(f"   - Files scanned: {total_files}")
    print(f"   - Files fixed: {len(fixed_files)}")
    print(f"   - HTTP URLs converted to HTTPS")
    
    if fixed_files:
        print(f"\n📝 Fixed files:")
        for file_path in fixed_files:
            print(f"   - {os.path.relpath(file_path, base_path)}")

if __name__ == "__main__":
    main()