package org.codehaus.mojo.unix.core;

import static fj.data.Option.some;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.VFS;
import org.codehaus.mojo.unix.FileAttributes;
import org.codehaus.mojo.unix.FileCollector;
import org.codehaus.mojo.unix.UnixFileMode;
import org.codehaus.mojo.unix.UnixFsObject;
import static org.codehaus.mojo.unix.core.AssemblyOperation.dirFromFileObject;
import static org.codehaus.mojo.unix.core.AssemblyOperation.fromFileObject;
import org.codehaus.mojo.unix.util.RelativePath;
import static org.codehaus.mojo.unix.util.RelativePath.fromString;
import org.codehaus.plexus.PlexusTestCase;
import org.easymock.AbstractMatcher;
import org.easymock.MockControl;

import static java.util.Arrays.asList;

/**
 * @author <a href="mailto:trygvis@codehaus.org">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class OperationTest
    extends PlexusTestCase
{
    public final static FileAttributes fileAttributes =
        new FileAttributes( some( "myuser" ), some( "mygroup" ), some( UnixFileMode._0755 ) );

    public final static FileAttributes directoryAttributes =
        new FileAttributes( some( "myuser" ), some( "mygroup" ), some( UnixFileMode._0644 ) );

    private static FileObject baseFileObject;

    public static FileObject getBaseFileObject()
        throws FileSystemException
    {
        if ( baseFileObject == null )
        {
            FileSystemManager fsManager = VFS.getManager();
            baseFileObject = fsManager.resolveFile( PlexusTestCase.getBasedir() );
        }

        return baseFileObject;
    }

    /**
     * Based on the <code>&gt;copy&lt;</code> operation that is in the jetty IT.
     */
    public void testCopyOnACompleteDirectoryStructure()
        throws Exception
    {
        FileObject files = getBaseFileObject().resolveFile( "src/test/resources/operation/files" );

        assertEquals( FileType.FOLDER, files.getType() );

        MockControl control = MockControl.createControl( FileCollector.class );
        FileCollector fileCollector = (FileCollector) control.getMock();

        RelativePath extraAppPath = fromString( "/opt/jetty/bin/extra-app" );
        FileObject extraPathObject = files.resolveFile( extraAppPath.string );
        fileCollector.addFile( extraPathObject, fromFileObject( extraAppPath, extraPathObject, fileAttributes ) );
        control.setMatcher( new FileObjectMatcher() );
        control.setReturnValue( fileCollector );

        RelativePath readmeUnixPath = fromString( "/opt/jetty/README-unix.txt" );
        FileObject readmeUnixPathObject = files.resolveFile( readmeUnixPath.string );
        fileCollector.addFile( readmeUnixPathObject,
                               fromFileObject( readmeUnixPath, readmeUnixPathObject, fileAttributes ) );
        control.setReturnValue( fileCollector );

        RelativePath bashProfilePath = fromString( "/opt/jetty/.bash_profile" );
        FileObject bashProfileObject = files.resolveFile( bashProfilePath.string );
        fileCollector.addFile( bashProfileObject,
                               fromFileObject( bashProfilePath, bashProfileObject, fileAttributes ) );
        control.setReturnValue( fileCollector );

        UnixFsObject.Directory directory = dirFromFileObject( RelativePath.BASE, files, directoryAttributes );

        control.expectAndReturn( fileCollector.addDirectory( directory.setPath( fromString( "/opt/jetty/bin" ) ) ),
                                 fileCollector );
        control.expectAndReturn( fileCollector.addDirectory( directory.setPath( fromString( "/opt/jetty/" ) ) ),
                                 fileCollector );
        control.expectAndReturn( fileCollector.addDirectory( directory.setPath( fromString( "/opt" ) ) ),
                                 fileCollector );
        control.expectAndReturn( fileCollector.addDirectory( directory ), fileCollector );
        control.replay();

        new CopyDirectoryOperation( files, RelativePath.BASE, null, null, null, null, fileAttributes,
                                    directoryAttributes ).
            perform( fileCollector );

        control.verify();
    }

    public void testExtractWithPattern()
        throws Exception
    {
        String archivePath = PlexusTestCase.getTestPath( "src/test/resources/operation/extract.jar" );

        FileSystemManager fsManager = VFS.getManager();
        FileObject archiveObject = fsManager.resolveFile( archivePath );
        assertEquals( FileType.FILE, archiveObject.getType() );
        FileObject archive = fsManager.createFileSystem( archiveObject );

        FileObject fooLicense = archive.getChild( "foo-license.txt" );
        UnixFsObject.RegularFile fooLicenseUnixFile =
            fromFileObject( fromString( "licenses/foo-license.txt" ), fooLicense, fileAttributes );

        FileObject barLicense = archive.getChild( "mydir" ).getChild( "bar-license.txt" );
        UnixFsObject.RegularFile barLicenseUnixFile =
            fromFileObject( fromString( "licenses/bar-license.txt" ), barLicense, fileAttributes );

        MockControl control = MockControl.createControl( FileCollector.class );
        FileCollector fileCollector = (FileCollector) control.getMock();

        control.expectAndReturn( fileCollector.addFile( barLicense, barLicenseUnixFile ), fileCollector );
        control.expectAndReturn( fileCollector.addFile( fooLicense, fooLicenseUnixFile ), fileCollector );
        control.replay();

        new CopyDirectoryOperation( archive, fromString( "licenses" ), asList( "**/*license.txt" ), null,
                                    ".*/(.*license.*)", "$1", fileAttributes, directoryAttributes ).
            perform( fileCollector );

        control.verify();
    }

    private static class FileObjectMatcher
        extends AbstractMatcher
    {
        int i = 0;

        protected boolean argumentMatches( Object e, Object a )
        {
            i++;
            if ( i != 1 )
            {
                return super.argumentMatches( e, a );
            }

            FileObject expected = (FileObject) e;
            FileObject actual = (FileObject) a;

            return MockControl.EQUALS_MATCHER.matches( new Object[]{expected.getName()},
                                                       new Object[]{actual.getName()} );
        }
    }
}
