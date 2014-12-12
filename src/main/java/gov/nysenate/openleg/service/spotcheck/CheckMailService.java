package gov.nysenate.openleg.service.spotcheck;

import gov.nysenate.openleg.model.base.Environment;
import gov.nysenate.openleg.model.daybreak.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.mail.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CheckMailService {
    private static final Logger logger = LoggerFactory.getLogger(CheckMailService.class);

    @Autowired
    Environment environment;

    @Value("${checkmail.host}")
    private String host;
    @Value("${checkmail.user}")
    private String user;
    @Value("${checkmail.pass}")
    private String password;

    @Value("${checkmail.receiving}")
    private String receivingFolder;
    @Value("${checkmail.processed}")
    private String processedFolder;

    private File daybreakStaginDir;

    @PostConstruct
    public void init() {
        daybreakStaginDir = new File(environment.getStagingDir(), "daybreak");
    }

    /**
     * Schedules the checkmail method to be run according to the cron supplied in the properties file
     */
    @Scheduled(cron = "${scheduler.checkmail.cron}")
    public void scheduledCheckMail() {
        if (environment.isCheckMailScheduled()) {
            checkMail();
        }
    }

    /**
     * Checks an email server for incoming daybreak emails.
     * If a full set of daybreak emails is detected, the daybreak file attachments are saved as daybreak files
     */
    public void checkMail() {
        Store store = null;
        try {
            logger.info("checking for daybreak emails...");
            store = getMailConnection();

            Folder sourceFolder = navigateToFolder(receivingFolder, store);
            Folder archiveFolder = navigateToFolder(processedFolder, store);
            sourceFolder.open(Folder.READ_WRITE);

            DaybreakReportSet<DaybreakMessage> reports = getReports(sourceFolder);
            Map<LocalDate, DaybreakReport<DaybreakMessage>> completeReports = reports.getCompleteReports();
            Map<LocalDate, DaybreakReport<DaybreakMessage>> partialReports = reports.getPartialReports();
            if (completeReports.size() > 0) {
                logger.info("{} complete daybreak reports found.  Saving...", completeReports.size());
                saveCompleteReports(completeReports, sourceFolder, archiveFolder);
                logger.info("Daybreak files saved.");
            } else if (partialReports.size() > 0) {
                logger.info("{} partial daybreak reports found.", partialReports.size());
            } else{
                logger.info("No daybreak reports found");
            }
        } catch (MessagingException | IOException ex) {
            logger.error("CheckMail Error\n{}", ExceptionUtils.getStackTrace(ex));
        } finally {
            try {
                if (store != null) {
                    store.close();
                }
            } catch (MessagingException ignored) {}
        }
    }

    /**
     * --- Internal Methods ---
     */

    /**
     * Establishes a connection to the check mail imap server and returns the connection object
     * @return
     * @throws MessagingException
     */
    private Store getMailConnection() throws MessagingException {
        Store store = Session.getDefaultInstance(System.getProperties()).getStore("imaps");
        try {
            store.connect(host, user, password);
        }
        catch (MessagingException ex) {
            store.close();
            throw ex;
        }
        return store;
    }

    /**
     * Extracts all valid daybreak messages from the given source folder
     *  and places each of them in a report within the report set
     *
     * @param sourceFolder
     * @return
     * @throws MessagingException
     */
    private DaybreakReportSet<DaybreakMessage> getReports(Folder sourceFolder) throws MessagingException {
        DaybreakReportSet<DaybreakMessage> reports = new DaybreakReportSet<>();
        for (Message message : sourceFolder.getMessages()) {
            if (DaybreakDocType.getMessageDocType(message.getSubject()) != null) {
                reports.insertDaybreakDocument(new DaybreakMessage(message));
            }
        }
        return reports;
    }

    private void saveCompleteReports(Map<LocalDate, DaybreakReport<DaybreakMessage>> completeReports,
                                     Folder sourceFolder, Folder archiveFolder)
            throws MessagingException, IOException {
        List<Message> toArchive = new ArrayList<>();
        for (DaybreakReport<DaybreakMessage> report : completeReports.values()) {
            String prefix = report.getReportDate().format(DateTimeFormatter.ofPattern(DaybreakFile.reportDateMatchPattern));
            for (DaybreakMessage daybreakMessage : report.getReportDocs().values()) {
                String filename = prefix + daybreakMessage.getDaybreakDocType().getLocalFileExt();
                Message message = daybreakMessage.getMessage();

                saveMessage(message, filename);
                toArchive.add(message);
                message.setFlag(Flags.Flag.DELETED, true);
            }
        }
        sourceFolder.copyMessages(toArchive.toArray(new Message[toArchive.size()]), archiveFolder);
        sourceFolder.expunge();
    }

    private void saveMessage(Message message, String filename) throws MessagingException, IOException {
        if (message.isMimeType("multipart/*")) {
            Multipart content = (Multipart) message.getContent();
            for (int i = 0; i < content.getCount(); i++) {
                Part part = content.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    logger.info("\tSaving " + part.getFileName() + " to " + filename);
                    String attachment = IOUtils.toString(part.getInputStream());

                    FileUtils.write(new File(daybreakStaginDir, filename), attachment);
                }
            }
        }
    }

    /**
     * Navigates through the given mail store to get the folder specified by the given path
     * @param path
     * @param store
     * @return
     * @throws MessagingException
     */
    private Folder navigateToFolder(String path, Store store) throws MessagingException {
        String[] splitPath = path.split("/");
        Folder folder = store.getFolder(splitPath[0]);
        for(int i=1; i<splitPath.length; i++) {
            folder = folder.getFolder(splitPath[i]);
        }
        return folder;
    }
}
